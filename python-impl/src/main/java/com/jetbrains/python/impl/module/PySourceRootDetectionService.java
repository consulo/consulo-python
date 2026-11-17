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
package com.jetbrains.python.impl.module;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.WriteAction;
import consulo.application.util.registry.Registry;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.RoamingType;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.content.ContentFolderTypeProvider;
import consulo.ide.impl.idea.ide.projectView.actions.MarkRootAction;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationAction;
import consulo.project.ui.notification.NotificationType;
import consulo.python.impl.localize.PySourceRootsLocalize;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@State(
    name = "PySourceRootDetectionService",
    storages = @Storage(value = "pySourceRootDetection.xml", roamingType = RoamingType.DISABLED)
)
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PySourceRootDetectionService implements PersistentStateComponent<PySourceRootDetectionService.HiddenSourcesState> {
    private final Project myProject;
    private HiddenSourcesState myState = new HiddenSourcesState();

    @Inject
    public PySourceRootDetectionService(Project project) {
        myProject = project;
        // TODO consulo: WorkspaceModel.eventLog missing - JB collects workspace model changes here and,
        //  when "python.source.root.suggest.quickfix.auto.apply" is enabled, hides every source root
        //  that was replaced or removed (EntityChange.Replaced/Removed of SourceRootEntity) via hideSourceRoot()
    }

    public static PySourceRootDetectionService getInstance(Project project) {
        return project.getInstance(PySourceRootDetectionService.class);
    }

    @Override
    public HiddenSourcesState getState() {
        return myState;
    }

    @Override
    public void loadState(HiddenSourcesState state) {
        myState = state;
    }

    private boolean isSourceRootHidden(VirtualFile sourceRoot) {
        return myState.sourcePathsSet.contains(sourceRoot.getPath());
    }

    private void hideSourceRoot(VirtualFile sourceRoot) {
        HiddenSourcesState newState = new HiddenSourcesState();
        newState.sourcePathsSet.addAll(myState.sourcePathsSet);
        newState.sourcePathsSet.add(sourceRoot.getPath());
        myState = newState;
    }

    public void markAsSourceRoot(VirtualFile sourceRoot) {
        markAsSourceRoot(sourceRoot, true);
    }

    public void markAsSourceRoot(VirtualFile sourceRoot, boolean showNotification) {
        WriteAction.runLater(() -> {
            Module module = ModuleUtilCore.findModuleForFile(sourceRoot, myProject);
            if (module == null) {
                return;
            }
            ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            ContentEntry entry = MarkRootAction.findContentEntry(model, sourceRoot);
            if (entry == null) {
                // Consulo: an uncommitted modifiable model must be disposed
                model.dispose();
                return;
            }
            // In general, `markAsSourceRoot` is called only for folders that are not source roots yet.
            // Double-check in case this method was called twice before the folder was marked as a source root.
            boolean isAlreadyMarkedAsSourceRoot = Arrays.stream(entry.getFolders(ContentFolderTypeProvider.allExceptExcluded()))
                .anyMatch(it -> sourceRoot.equals(it.getFile()));
            if (isAlreadyMarkedAsSourceRoot) {
                // Consulo: an uncommitted modifiable model must be disposed
                model.dispose();
                return;
            }
            entry.addFolder(sourceRoot, ProductionContentFolderTypeProvider.getInstance());
            model.commit();

            if (showNotification) {
                showNotification(sourceRoot);
            }
        });
    }

    private void unmarkAsSourceRoot(VirtualFile sourceRoot) {
        unmarkAsSourceRoot(sourceRoot, true);
    }

    private void unmarkAsSourceRoot(VirtualFile sourceRoot, boolean doNotSuggestSourceRootAgain) {
        if (doNotSuggestSourceRootAgain) {
            hideSourceRoot(sourceRoot);
        }
        WriteAction.runLater(() -> {
            Module module = ModuleUtilCore.findModuleForFile(sourceRoot, myProject);
            if (module == null) {
                return;
            }
            ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            ContentEntry entry = MarkRootAction.findContentEntry(model, sourceRoot);
            if (entry == null) {
                // Consulo: an uncommitted modifiable model must be disposed
                model.dispose();
                return;
            }
            // JB: sourceFolders.firstOrNull { it.file == this || it.url == sourceRoot.url }
            // ("this" is the service there, so only the url comparison can match)
            ContentFolder toRemove = Arrays.stream(entry.getFolders(ContentFolderTypeProvider.allExceptExcluded()))
                .filter(it -> it.getUrl().equals(sourceRoot.getUrl()))
                .findFirst()
                .orElse(null);
            if (toRemove != null) {
                entry.removeFolder(toRemove);
            }
            model.commit();
        });
    }

    public void onSourceRootDetected(VirtualFile sourceRootPath) {
        if (isSourceRootHidden(sourceRootPath)) {
            return;
        }
        markAsSourceRoot(sourceRootPath, true);
    }

    private String getSourceRootVisibleName(VirtualFile sourceRoot) {
        // TODO consulo: Project.guessProjectDir missing, Project.getBaseDir used instead
        VirtualFile projectDir = myProject.getBaseDir();
        if (projectDir == null) {
            return sourceRoot.getName();
        }
        String relativePath = VirtualFileUtil.getRelativePath(sourceRoot, projectDir, File.separatorChar);
        return relativePath != null ? relativePath.replace("\\", "/") : sourceRoot.getName();
    }

    private void showNotification(VirtualFile sourceRoot) {
        String message = PySourceRootsLocalize.pythonSourceRootDetectionConfirmNotificationTitle(getSourceRootVisibleName(sourceRoot)).get();
        Notification notification =
            PySourceRootDetectionNotificationGroupContributor.PYTHON_SOURCE_ROOT_DETECTION.createNotification(message, NotificationType.INFORMATION);

        notification.addAction(new OkAction());
        notification.addAction(new RevertAction(sourceRoot));
        notification.addAction(new MuteAction());

        notification.notify(myProject);
    }

    public void resetState() {
        myState = new HiddenSourcesState();
    }

    public static class HiddenSourcesState {
        public Set<String> sourcePathsSet = new LinkedHashSet<>();
    }

    private static class OkAction extends NotificationAction {
        OkAction() {
            super(PySourceRootsLocalize.pythonSourceRootDetectionConfirmNotificationActionOk());
        }

        @Override
        public void actionPerformed(AnActionEvent e, Notification notification) {
            notification.expire();
        }
    }

    private class RevertAction extends NotificationAction {
        private final VirtualFile mySourceRoot;

        RevertAction(VirtualFile sourceRoot) {
            super(PySourceRootsLocalize.pythonSourceRootDetectionConfirmNotificationActionRevert());
            mySourceRoot = sourceRoot;
        }

        @Override
        public void actionPerformed(AnActionEvent e, Notification notification) {
            unmarkAsSourceRoot(mySourceRoot);
            notification.expire();
        }
    }

    private static class MuteAction extends NotificationAction {
        MuteAction() {
            super(PySourceRootsLocalize.pythonSourceRootDetectionConfirmNotificationActionMute());
        }

        @Override
        public void actionPerformed(AnActionEvent e, Notification notification) {
            Registry.get("python.source.root.suggest.quickfix.auto.apply").setValue(false);
            notification.expire();
        }
    }
}
