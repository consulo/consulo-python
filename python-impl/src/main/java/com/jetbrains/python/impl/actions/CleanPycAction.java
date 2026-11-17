// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PyNames;
import consulo.application.progress.ProgressManager;
import consulo.language.content.FileIndexFacade;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.io.NioFiles;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class CleanPycAction extends AnAction implements AnActionWithSyncUpdate {
    public CleanPycAction() {
        super(PyCodeInsightLocalize.actionCleanpycText(), PyCodeInsightLocalize.actionCleanpycDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        PsiElement[] elements = e.getData(PsiElement.KEY_OF_ARRAY);
        if (!isAllDirectories(elements)) {
            return;
        }

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                try {
                    for (PsiElement element : elements) {
                        VirtualFile vfsDir = ((PsiDirectory) element).getVirtualFile();
                        Path nioDir = vfsDir.getFileSystem().getNioPath(vfsDir);
                        if (nioDir == null || !Files.isDirectory(nioDir)) {
                            continue;
                        }
                        Files.walkFileTree(nioDir, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                if (PyNames.PYCACHE.equals(NioFiles.getFileName(dir))) {
                                    FileUtil.delete(dir);
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                String name = NioFiles.getFileName(file);
                                if (name.endsWith(".pyc") || name.endsWith(".pyo") || name.endsWith("$py.class")) {
                                    Files.deleteIfExists(file);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                }
                catch (IOException ex) {
                    Logger.getInstance(CleanPycAction.class).warn(ex);
                }
            },
            PyCodeInsightLocalize.actionCleanpycProgressTitleCleaningUpPycFiles(),
            false,
            e.getData(Project.KEY)
        );
    }

    @Override
    public void update(AnActionEvent e) {
        PsiElement[] elements = e.getData(PsiElement.KEY_OF_ARRAY);
        if (e.isFromContextMenu()) {
            e.getPresentation().setEnabledAndVisible(isAllDirectories(elements));
        }
        else {
            e.getPresentation().setEnabled(isAllDirectories(elements));
        }
    }

    private static boolean isAllDirectories(PsiElement @Nullable [] elements) {
        return elements != null && elements.length > 0 && ContainerUtil.all(
            elements,
            element -> element instanceof PsiDirectory pd
                && !FileIndexFacade.getInstance(pd.getProject()).isInLibraryClasses(pd.getVirtualFile())
        );
    }
}
