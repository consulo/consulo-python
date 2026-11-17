// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.util.ModificationTracker;
import consulo.component.util.SimpleModificationTracker;
import consulo.disposer.Disposable;
import consulo.document.event.FileDocumentManagerListener;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.event.PsiTreeChangeAdapter;
import consulo.language.psi.event.PsiTreeChangeEvent;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.project.content.scope.ProjectScopes;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class PyLibraryModificationTracker implements ModificationTracker, Disposable {
    // TODO consulo: ThrottledLogger missing
    private static final Logger THROTTLED_LOG = Logger.getInstance(PyLibraryModificationTracker.class);

    private final ModificationTracker myProjectRootManager;
    private final ModificationTracker myDumbServiceModificationTracker;
    // PsiClass from libraries may become invalid on reparse
    // TODO consulo: ForcefulReparseModificationTracker missing
    private final SimpleModificationTracker myOnContentReloadModificationTracker = new SimpleModificationTracker();
    private final Throwable myCreationStack = new Throwable();
    private final ProjectAwareSearchScope myProjectLibraryScope;

    public static PyLibraryModificationTracker getInstance(Project project) {
        return project.getInstance(PyLibraryModificationTracker.class);
    }

    @Inject
    public PyLibraryModificationTracker(Project project) {
        myProjectRootManager = ProjectRootManager.getInstance(project);
        myDumbServiceModificationTracker = DumbService.getInstance(project).getModificationTracker();
        myProjectLibraryScope = ProjectScopes.getLibrariesScope(project);

        MessageBusConnection connection = project.getMessageBus().connect(this);
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(PsiTreeChangeEvent event) {
                PsiFile file = event.getFile();
                if (file == null) {
                    return;
                }
                VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile == null) {
                    return;
                }
                if (isLibraryFile(virtualFile)) {
                    myOnContentReloadModificationTracker.incModificationCount();
                }
            }
        }, this);

        connection.subscribe(FileDocumentManagerListener.class, new FileDocumentManagerListener() {
            @Override
            public void fileWithNoDocumentChanged(VirtualFile file) {
                if (!project.isInitialized()) {
                    THROTTLED_LOG.warn(
                        "SearchScope.contains(file) would log an error because WorkspaceFileIndex is not yet initialized. " +
                            "Probably LibraryModificationTracker was created too early. " +
                            "See LibraryModificationTracker creation stacktrace: ",
                        myCreationStack
                    );
                    return;
                }
                if (isLibraryFile(file)) {
                    myOnContentReloadModificationTracker.incModificationCount();
                }
            }
        });
    }

    public ProjectAwareSearchScope getProjectLibraryScope() {
        return myProjectLibraryScope;
    }

    private boolean isLibraryFile(VirtualFile file) {
        return "pyi".equals(file.getExtension()) || myProjectLibraryScope.contains(file);
    }

    @Override
    public long getModificationCount() {
        return myProjectRootManager.getModificationCount()
            + myDumbServiceModificationTracker.getModificationCount()
            + myOnContentReloadModificationTracker.getModificationCount();
    }


    @Override
    public void dispose() {
    }
}
