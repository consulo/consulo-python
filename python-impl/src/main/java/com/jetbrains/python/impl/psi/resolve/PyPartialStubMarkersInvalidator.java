// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.EditorFactory;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.document.FileDocumentManager;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.ui.ex.awt.util.Alarm;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileCopyEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.event.VFilePropertyChangeEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PyPartialStubMarkersInvalidator implements Disposable {
    private static final int INVALIDATION_DELAY = 300;

    private final Project project;
    private final Alarm invalidationRequests;

    @Inject
    public PyPartialStubMarkersInvalidator(Project project) {
        this.project = project;
        // Consulo: a debouncing Alarm takes the place of the JB MutableSharedFlow + debounce + collectLatest
        this.invalidationRequests = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    }

    public static PyPartialStubMarkersInvalidator getInstance(Project project) {
        return project.getInstance(PyPartialStubMarkersInvalidator.class);
    }

    public void subscribe() {
        Disposable parentDisposable = this;

        project.getMessageBus().connect(parentDisposable).subscribe(BulkFileListener.class, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    if (affectsPartialStubMarkers(project, event)) {
                        tryEmit();
                        break;
                    }
                }
            }
        });

        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent event) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (file == null) {
                    return;
                }
                if (isProjectPartialStubMarkerFile(project, file)) {
                    tryEmit();
                }
            }
        }, parentDisposable);
    }

    private void tryEmit() {
        if (invalidationRequests.isDisposed()) {
            return;
        }
        invalidationRequests.cancelAllRequests();
        invalidationRequests.addRequest(() -> clearPathCaches(project), INVALIDATION_DELAY);
    }

    private static void clearPathCaches(Project project) {
        if (project.isDisposed()) {
            return;
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            PythonModulePathCache.getInstance(module).clearCache();
        }

        // Debounced on a coroutine, so it can await the structure. The set holds the main interpreter too.
        for (Sdk interpreter : pythonInterpreters(project)) {
            PythonSdkPathCache.getInstance(project, interpreter).clearCache();
        }
    }

    /**
     * Consulo: JB {@code project.pythonInterpreters()} - the interpreters of the Python modules plus the main one.
     */
    private static Set<Sdk> pythonInterpreters(Project project) {
        Set<Sdk> result = new LinkedHashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            Sdk sdk = PythonSdkType.findPythonSdk(module);
            if (sdk != null) {
                result.add(sdk);
            }
        }
        Sdk main = MainPythonSdkKt.mainPythonSdk(project);
        if (main != null) {
            result.add(main);
        }
        return result;
    }

    private static boolean isPartialStubMarkerFileName(@Nullable String fileName) {
        return "py.typed".equals(fileName);
    }

    private static boolean affectsPartialStubMarkers(Project project, VFileEvent event) {
        VirtualFile eventFile = event.getFile();
        if (eventFile != null && isProjectPartialStubMarkerFile(project, eventFile)) {
            return true;
        }

        if (event instanceof VFileCopyEvent copyEvent) {
            return isPartialStubMarkerFileName(copyEvent.getNewChildName()) &&
                isProjectRelevantLocation(project, copyEvent.getNewParent());
        }
        else if (event instanceof VFilePropertyChangeEvent propertyChangeEvent) {
            if (!propertyChangeEvent.isRename()) {
                return false;
            }
            String oldValue = propertyChangeEvent.getOldValue() instanceof String s ? s : null;
            String newValue = propertyChangeEvent.getNewValue() instanceof String s ? s : null;
            if (!isPartialStubMarkerFileName(oldValue) && !isPartialStubMarkerFileName(newValue)) {
                return false;
            }
            VirtualFile parent = propertyChangeEvent.getFile().getParent();
            return parent != null && isProjectRelevantLocation(project, parent);
        }
        return false;
    }

    private static boolean isProjectPartialStubMarkerFile(Project project, VirtualFile file) {
        return isPartialStubMarkerFileName(file.getName()) && isProjectRelevantLocation(project, file);
    }

    private static boolean isProjectRelevantLocation(Project project, VirtualFile file) {
        if (!project.isInitialized() || !file.isValid()) {
            return false;
        }

        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        // TODO consulo: ProjectFileIndex.isInProject missing
        return index.isInContent(file) || index.isInLibrary(file);
    }

    @Override
    public void dispose() {
    }
}
