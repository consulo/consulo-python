// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.registry.Registry;
import consulo.component.messagebus.MessageBusConnection;
import consulo.content.bundle.Sdk;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.file.FileTypeManager;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.FilePropertyPusher;
import consulo.module.content.ModuleFileIndex;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.PushedFilePropertiesUpdater;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.project.DumbModeTask;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.project.localize.ProjectLocalize;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.VFileProperty;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileWithId;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import org.jspecify.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ExtensionImpl
public final class PythonLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
    // TODO consulo: FilePropertyKeyImpl.createPersistentEnumKey missing - the pushed value is kept in LanguageLevel.KEY
    //  and persisted with a FileAttribute
    private static final FileAttribute PERSISTENCE = new FileAttribute("python_language_level_persistence", 3, true);

    /* It so happens that no single language level is compatible with more than one other.
       So a map suffices for representation*/
    private static final Map<LanguageLevel, LanguageLevel> COMPATIBLE_LEVELS;

    static {
        Map<LanguageLevel, LanguageLevel> compatLevels = new EnumMap<>(LanguageLevel.class);
        addCompatiblePair(compatLevels, LanguageLevel.PYTHON26, LanguageLevel.PYTHON27);
        addCompatiblePair(compatLevels, LanguageLevel.PYTHON33, LanguageLevel.PYTHON34);
        COMPATIBLE_LEVELS = Collections.unmodifiableMap(compatLevels);
    }

    private static void addCompatiblePair(Map<LanguageLevel, LanguageLevel> levels, LanguageLevel l1, LanguageLevel l2) {
        levels.put(l1, l2);
        levels.put(l2, l1);
    }

    /**
     * Pushes the layout of the current snapshot of [project] before its first scan, and then each new layout.
     */
    @Override
    public void initExtra(Project project) {
        PythonLanguageLevels.getInstance(project).start();
    }

    @Override
    public Key<LanguageLevel> getFileDataKey() {
        return LanguageLevel.KEY;
    }

    @Override
    public boolean pushDirectoriesOnly() {
        return true;
    }

    @Override
    public LanguageLevel getDefaultValue() {
        return LanguageLevel.getDefault();
    }

    @Override
    public @Nullable LanguageLevel getImmediateValue(Project project, @Nullable VirtualFile file) {
        return null;
    }

    /**
     * The level of [file] from the interpreter that holds it: the interpreter of its module, or an interpreter whose class
     * root holds a file outside the modules. {@code null} when no interpreter holds it.
     */
    private static @Nullable LanguageLevel getFileInterpreterLevel(Project project,
                                                                   PythonLanguageLevelLayout layout,
                                                                   VirtualFile file) {
        final Module module = ModuleUtilCore.findModuleForFile(file, project);
        return module != null ? layout.getModuleLevels().get(module) : layout.levelOfInterpreterFile(file);
    }

    @Override
    public LanguageLevel getImmediateValue(Module module) {
        if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
            return LanguageLevel.FORCE_LANGUAGE_LEVEL;
        }

        final LanguageLevel level = PythonLanguageLevels.layoutOf(module.getProject()).getModuleLevels().get(module);
        return level != null ? level : LanguageLevel.getDefault();
    }

    @Override
    public boolean acceptsFile(VirtualFile file, Project project) {
        return false;
    }

    @Override
    public boolean acceptsDirectory(VirtualFile file, Project project) {
        return true;
    }

    private static boolean areLanguageLevelsCompatible(@Nullable LanguageLevel oldLevel, @Nullable LanguageLevel newLevel) {
        return oldLevel != null && newLevel != null && COMPATIBLE_LEVELS.get(oldLevel) == newLevel;
    }

    @Override
    public void persistAttribute(Project project, VirtualFile fileOrDir, LanguageLevel level) throws IOException {
        LanguageLevel oldLanguageLevel = getPersistentValue(fileOrDir);
        boolean changed = setPersistentValue(fileOrDir, level, oldLanguageLevel);
        if (!changed) {
            return;
        }

        if (!areLanguageLevelsCompatible(oldLanguageLevel, level) || !ProjectFileIndex.getInstance(project).isInContent(fileOrDir)) {
            PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, PythonLanguageLevelPusher::isPythonFile);
        }
        if (fileOrDir.isDirectory() || isPythonFile(fileOrDir)) {
            clearSdkPathCache(fileOrDir);
        }

        VirtualFilePyLanguageLevelListener publisher = project.getMessageBus().syncPublisher(VirtualFilePyLanguageLevelListener.class);
        publisher.levelChanged(fileOrDir, level);

        for (VirtualFile child : fileOrDir.getChildren()) {
            if (!child.isDirectory() && isPythonFile(child)) {
                publisher.levelChanged(child, level);
            }
        }
    }

    /**
     * Consulo: stands in for {@code FilePropertyKey.getPersistentValue}.
     */
    private static @Nullable LanguageLevel getPersistentValue(VirtualFile fileOrDir) throws IOException {
        try (DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir)) {
            if (iStream != null) {
                int ordinal = DataInputOutputUtil.readINT(iStream);
                LanguageLevel[] values = LanguageLevel.values();
                return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
            }
        }
        return null;
    }

    /**
     * Consulo: stands in for {@code FilePropertyKey.setPersistentValue}, returns whether the value changed.
     */
    private static boolean setPersistentValue(VirtualFile fileOrDir, LanguageLevel level, @Nullable LanguageLevel oldLevel)
        throws IOException {
        if (oldLevel == level) {
            return false;
        }
        try (DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir)) {
            DataInputOutputUtil.writeINT(oStream, level.ordinal());
        }
        return true;
    }

    private static boolean isPythonFile(VirtualFile child) {
        return PythonFileType.INSTANCE.equals(FileTypeRegistry.getInstance().getFileTypeByFileName(child.getNameSequence()));
    }

    private static void clearSdkPathCache(final VirtualFile child) {
        final Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            final Module module = ModuleUtilCore.findModuleForFile(child, project);
            final Sdk interpreter = module == null ? null : findModuleInterpreter(module);
            if (interpreter != null) {
                PythonSdkPathCache.getInstance(project, interpreter).clearCache();
            }
        }
    }

    @Override
    public void afterRootsChanged(final Project project) {
        // TODO consulo: the snapshot flow of PythonLanguageLevels is missing, so a roots change (which a change of a module
        //  SDK is in Consulo) also takes the new layout here
        final PythonLanguageLevelLayout previous = PythonLanguageLevels.getInstance(project).updateLayout();
        // A roots change can add a root that has no level yet, so the current layout is pushed again.
        final PythonLanguageLevelLayout layout = PythonLanguageLevels.layoutOf(project);
        doPush(project, layout, previous);
    }

    /**
     * Pushes [layout], which follows [previous], see {@link PythonLanguageLevels#start}. For a change that no roots change
     * reports: a new snapshot, or a new version that the interpreter store saves.
     */
    static void push(Project project, PythonLanguageLevelLayout layout, PythonLanguageLevelLayout previous) {
        if (project.isDisposed()) {
            return;
        }
        for (FilePropertyPusher<?> pusher : project.getApplication().getExtensionList(FilePropertyPusher.class)) {
            if (pusher instanceof PythonLanguageLevelPusher languageLevelPusher) {
                languageLevelPusher.doPush(project, layout, previous);
            }
        }
    }

    /**
     * Queues a task that pushes the levels of [layout] to the roots. Reparses the open files when the level of a module
     * changed since [previous].
     */
    private void doPush(Project project, PythonLanguageLevelLayout layout, PythonLanguageLevelLayout previous) {
        boolean levelOfModuleChanged = false;
        for (Map.Entry<Module, LanguageLevel> entry : layout.getModuleLevels().entrySet()) {
            final LanguageLevel before = previous.getModuleLevels().get(entry.getKey());
            if (before != null && before != entry.getValue()) {
                levelOfModuleChanged = true;
                break;
            }
        }
        if (!layout.getModuleLevels().isEmpty() || !layout.getInterpreterLevels().isEmpty()) {
            new MyDumbModeTask(project, layout).queue(project);
        }
        if (levelOfModuleChanged) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                PythonCodeStyleService.getInstance().reparseOpenEditorFiles(project);
            });
        }
    }

    /**
     * The interpreter of [module] in the current snapshot, or {@code null}.
     */
    private static @Nullable Sdk findModuleInterpreter(Module module) {
        // TODO consulo: EvoPyProjectModelKt.findEvoPyProjectIfReady missing, the interpreter is the module SDK
        return PythonSdkType.findPythonSdk(module);
    }

    private List<Runnable> getRootUpdateTasks(Project project, PythonLanguageLevelLayout layout) {
        final List<Runnable> results = new ArrayList<>();
        for (Map.Entry<Module, LanguageLevel> moduleLevel : layout.getModuleLevels().entrySet()) {
            final Module module = moduleLevel.getKey();
            if (module.isDisposed()) {
                continue;
            }

            for (VirtualFile root : PyUtil.getSourceRoots(module)) {
                addRootIndexingTask(root, results, project, moduleLevel.getValue(), true);
            }
        }
        for (Map.Entry<Sdk, LanguageLevel> interpreterLevel : layout.getInterpreterLevels().entrySet()) {
            final Sdk interpreter = interpreterLevel.getKey();
            // TODO consulo: PythonInterpreterExtKt.isDisposed missing

            for (VirtualFile root : PythonLanguageLevelLayout.classRoots(interpreter)) {
                if (!root.isValid() || PyTypeShed.INSTANCE.isInside(root)) {
                    continue;
                }
                addRootIndexingTask(root, results, project, interpreterLevel.getValue(), false);
            }
        }
        return results;
    }

    private void addRootIndexingTask(VirtualFile root,
                                     List<Runnable> results,
                                     Project project,
                                     LanguageLevel languageLevel,
                                     boolean iterateAsContent) {
        final VirtualFile parent = root.getParent();
        final boolean shouldSuppressSizeLimit = parent != null && parent.getName().equals(PythonSdkType.SKELETON_DIR_NAME);
        results.add(new UpdateRootTask(project, root, languageLevel, shouldSuppressSizeLimit, iterateAsContent));
    }

    /**
     * @deprecated Use {@link PyLanguageFacade#getEffectiveLanguageLevel(Project, VirtualFile)}
     */
    @Deprecated(forRemoval = true)
    public static LanguageLevel getLanguageLevelForVirtualFile(Project project, VirtualFile virtualFile) {
        return PyLanguageFacade.getInstance().getEffectiveLanguageLevel(project, virtualFile);
    }

    // Use `PyLanguageFacade.getEffectiveLanguageLevel(Project, VirtualFile)`
    public static LanguageLevel getEffectiveLanguageLevel(Project project, VirtualFile virtualFile) {
        final LanguageLevel forced = LanguageLevel.FORCE_LANGUAGE_LEVEL;
        if (ApplicationManager.getApplication().isUnitTestMode() && forced != null) {
            return forced;
        }

        final LanguageLevel specified = specifiedFileLanguageLevel(virtualFile);
        if (specified != null) {
            return specified;
        }

        final PythonLanguageLevelLayout layout = PythonLanguageLevels.layoutOf(project);
        final LanguageLevel interpreterLevel =
            virtualFile instanceof LightVirtualFile ? null : getFileInterpreterLevel(project, layout, virtualFile);
        if (interpreterLevel != null) {
            return interpreterLevel;
        }

        return layout.getProjectLevel();
    }

    private final class UpdateRootTask implements Runnable {
        private final Project myProject;
        private final VirtualFile myRoot;
        private final LanguageLevel myLanguageLevel;
        private final boolean myShouldSuppressSizeLimit;
        private final boolean myIterateAsContent;

        UpdateRootTask(Project project, VirtualFile root, LanguageLevel languageLevel,
                       boolean shouldSuppressSizeLimit, boolean iterateAsContent) {
            myProject = project;
            myRoot = root;
            myLanguageLevel = languageLevel;
            myShouldSuppressSizeLimit = shouldSuppressSizeLimit;
            myIterateAsContent = iterateAsContent;
        }

        @Override
        public void run() {
            // This code is a copy-pasted behaviour of ModuleIndexableFilesIteratorImpl and SdkIndexableFilesIteratorImpl.
            // Since they are planned to become obsolete next release, and API to iterate any indexable directory/file would be provided,
            // this dirty solution is good enough
            if (myIterateAsContent) {
                ModuleFileIndex index = ReadAction.compute(() -> {
                    if (myProject.isDisposed() || !myRoot.isValid()) {
                        return null;
                    }
                    Module module = ProjectFileIndex.getInstance(myProject).getModuleForFile(myRoot, false);
                    if (module == null) {
                        return null;
                    }
                    return ModuleRootManager.getInstance(module).getFileIndex();
                });
                if (index == null) {
                    return;
                }

                final PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(myProject);
                // TODO consulo: ContentIteratorEx / TreeNodeProcessingResult.SKIP_CHILDREN missing - the children of a file that
                //  is not pushed are still iterated
                index.iterateContentUnderDirectory(myRoot, file -> {
                    visitFileToPush(file, propertiesUpdater);
                    return true;
                });
                return;
            }

            if (myProject.isDisposed() || !ReadAction.compute(() -> myRoot.isValid())) {
                return;
            }
            final PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(myProject);

            ProjectFileIndex projectFileIndex = ReadAction.compute(() -> {
                if (myProject.isDisposed()) {
                    return null;
                }
                return ProjectFileIndex.getInstance(myProject);
            });
            if (projectFileIndex == null) {
                return;
            }
            VirtualFileUtil.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(VirtualFile file) {
                    if (!shouldVisit(file, projectFileIndex, myRoot)) {
                        return false;
                    }
                    return visitFileToPush(file, propertiesUpdater);
                }
            });
        }

        private static boolean shouldVisit(VirtualFile file, ProjectFileIndex index, VirtualFile root) {
            if (file.is(VFileProperty.SYMLINK)) {
                if (!Registry.is("indexer.follows.symlinks", true)) {
                    return false;
                }
                VirtualFile targetFile = file.getCanonicalFile();
                if (targetFile == null || targetFile.is(VFileProperty.SYMLINK)) {
                    // Broken or recursive symlink. The second check should not happen but let's guarantee no StackOverflowError.
                    return false;
                }
                if (root.equals(file)) {
                    return true;
                }
                return shouldVisit(targetFile, index, root);
            }
            if (!(file instanceof VirtualFileWithId) || ((VirtualFileWithId) file).getId() <= 0) {
                return false;
            }
            if (ReadAction.compute(() -> index.isExcluded(file))) {
                return false;
            }
            return true;
        }

        private Boolean visitFileToPush(VirtualFile file, PushedFilePropertiesUpdater propertiesUpdater) {
            // Copied from `com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.applyPushersToFile`.
            // Preload children outside read action to avoid freezes.
            if (file.isDirectory()) {
                file.getChildren();
            }
            return ReadAction.compute(() -> {
                if (!file.isValid() || FileTypeManager.getInstance().isFileIgnored(file)) {
                    return false;
                }
                if (file.isDirectory()) {
                    propertiesUpdater.findAndUpdateValue(
                        file,
                        PythonLanguageLevelPusher.this,
                        myLanguageLevel
                    );
                }
                if (myShouldSuppressSizeLimit) {
                    SingleRootFileViewProvider.doNotCheckFileSizeLimit(file);
                }
                return true;
            });
        }

        @Override
        public String toString() {
            return "UpdateRootTask{" +
                "myRoot=" + myRoot +
                ", myLanguageLevel=" + myLanguageLevel +
                '}';
        }
    }

    public static void setForcedLanguageLevel(Project project, @Nullable LanguageLevel languageLevel) {
        LanguageLevel.FORCE_LANGUAGE_LEVEL = languageLevel;
        PushedFilePropertiesUpdater.getInstance(project).pushAll(new PythonLanguageLevelPusher());
    }

    public static void specifyFileLanguageLevel(VirtualFile file, @Nullable LanguageLevel languageLevel) {
        // TODO consulo: FilePropertyKey (persistent file property) missing, the level is kept in the file user data
        file.putUserData(LanguageLevel.KEY, languageLevel);
    }

    private static @Nullable LanguageLevel specifiedFileLanguageLevel(@Nullable VirtualFile file) {
        if (file == null) {
            return null;
        }

        final LanguageLevel specified = file.getUserData(LanguageLevel.KEY);
        if (file.isDirectory()) {
            // no need to check parent since UpdateRootTask pushes language level into all directories under roots
            return specified;
        }
        else {
            return specified == null ? specifiedFileLanguageLevel(file.getParent()) : specified;
        }
    }

    private final class MyDumbModeTask extends DumbModeTask {
        private final Project project;
        private final PythonLanguageLevelLayout layout;
        private final MessageBusConnection connection;

        private MyDumbModeTask(Project project, PythonLanguageLevelLayout layout) {
            this.project = project;
            this.layout = layout;
            connection = project.getMessageBus().connect();
            connection.subscribe(ModuleRootListener.class, new ModuleRootListener() {
                @Override
                public void rootsChanged(ModuleRootEvent event) {
                    DumbService.getInstance(project).cancelTask(MyDumbModeTask.this);
                }
            });
        }

        @Override
        public void dispose() {
            connection.disconnect();
        }

        @Override
        public void performInDumbMode(ProgressIndicator indicator, Exception trace) {
            if (project.isDisposed()) {
                return;
            }

            //final PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
            indicator.setIndeterminate(true);
            indicator.setText(ProjectLocalize.progressIndexingScanning());
            final List<Runnable> tasks = ReadAction.compute(() -> getRootUpdateTasks(project, layout));
            // The push reindexes every indexed Python file, and each reindex drops the PSI caches in a write action.
            // The token merges these drops into one write action.
            // TODO consulo: GistManager.mergeDependentCacheInvalidations missing
            PushedFilePropertiesUpdater.getInstance(project).runConcurrentlyIfPossible(tasks);
            //if (!ApplicationManager.getApplication().isUnitTestMode()) {
            //  snapshot.logResponsivenessSinceCreation("Pushing Python language level to " + tasks.size() + " roots in " + sdks.size() +
            //                                          " SDKs");
            //}
        }

        @Override
        public @Nullable DumbModeTask tryMergeWith(DumbModeTask taskFromQueue) {
            if (taskFromQueue instanceof MyDumbModeTask queued && queued.project.equals(project) && queued.layout.equals(layout)) {
                return this;
            }
            return null;
        }
    }
}
