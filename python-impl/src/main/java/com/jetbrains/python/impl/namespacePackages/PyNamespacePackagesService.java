// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.namespacePackages;

import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.impl.FoldersComponentTools;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.ProjectView;
import consulo.virtualFileSystem.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyNamespacePackagesService {
    private final List<VirtualFile> myNamespacePackageFolders = new ArrayList<>();
    private final FoldersComponentTools myTools = new FoldersComponentTools(myNamespacePackageFolders);
    private final Module myModule;

    public PyNamespacePackagesService(Module module) {
        myModule = module;
    }

    public static PyNamespacePackagesService getInstance(Module module) {
        return PyNamespacePackagesServiceFactory.getInstance(module.getProject()).getService(module);
    }

    public List<VirtualFile> getNamespacePackageFoldersVirtualFiles() {
        removeInvalidNamespacePackageFolders();
        return Collections.unmodifiableList(myNamespacePackageFolders);
    }

    public void setNamespacePackageFolders(List<String> folders) {
        myTools.setFoldersAsStrings(folders);
    }

    public void setNamespacePackageFoldersVirtualFiles(List<VirtualFile> folders) {
        myTools.setFoldersAsVirtualFiles(folders);
    }

    public void toggleMarkingAsNamespacePackage(VirtualFile directory) {
        // TODO consulo: SideEffectGuard missing - SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS)
        if (!directory.isDirectory()) {
            return;
        }

        if (canBeMarked(directory)) {
            myNamespacePackageFolders.add(directory);
            // TODO consulo: FUS statistics missing - PyNamespacePackagesStatisticsCollector.logToggleMarkingAsNamespacePackage(true)
        }
        else if (isMarked(directory)) {
            myNamespacePackageFolders.remove(directory);
            // TODO consulo: FUS statistics missing - PyNamespacePackagesStatisticsCollector.logToggleMarkingAsNamespacePackage(false)
        }
        else {
            throw new IllegalStateException("Can't toggle namespace package state for: " + directory.getName());
        }

        refreshView();
    }

    public boolean canBeMarked(VirtualFile virtualFile) {
        Project project = myModule.getProject();

        if (PyLanguageFacade.getInstance().getEffectiveLanguageLevel(project, virtualFile).isOlderThan(LanguageLevel.PYTHON34)) {
            return false;
        }
        if (PyUtil.isRoot(virtualFile, project)) {
            return false;
        }
        if (!isInProject(virtualFile, project)) {
            return false;
        }
        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
        if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) {
            return false;
        }

        VirtualFile curDir = virtualFile;
        while (curDir != null) {
            if (!curDir.isDirectory()) {
                return false;
            }
            if (myNamespacePackageFolders.contains(curDir)) {
                return false;
            }
            if (PyUtil.isRoot(curDir, project)) {
                break;
            }
            psiDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(curDir);
            if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) {
                break;
            }
            curDir = curDir.getParent();
        }

        return true;
    }

    public boolean isMarked(VirtualFile dir) {
        return myNamespacePackageFolders.contains(dir);
    }

    public boolean isNamespacePackage(VirtualFile directory) {
        PsiDirectory psiDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(directory);
        if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) {
            return false;
        }
        VirtualFile curDir = directory;
        while (curDir != null) {
            if (isMarked(curDir)) {
                return true;
            }
            if (PyUtil.isRoot(directory, myModule.getProject())) {
                break;
            }
            psiDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(curDir);
            if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) {
                break;
            }
            curDir = curDir.getParent();
        }
        return false;
    }

    public static boolean isEnabled() {
        // JB registry key "python.explicit.namespace.packages" (defaultValue="true", intellij.python.psi.impl.xml)
        return Registry.is("python.explicit.namespace.packages", true);
    }

    private void removeInvalidNamespacePackageFolders() {
        myNamespacePackageFolders.removeIf(it -> it == null || !it.isValid());
    }

    private static boolean isInProject(VirtualFile virtualFile, Project project) {
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiDirectory psiDirectory = psiManager.findDirectory(virtualFile);
        if (psiDirectory == null) {
            return false;
        }
        if (!psiManager.isInProject(psiDirectory)) {
            return false;
        }
        return true;
    }

    private void refreshView() {
        // TODO consulo: Application.isWriteIntentLockAcquired missing, dispatch thread check used instead
        if (!Application.get().isDispatchThread()) {
            return;
        }
        Project project = myModule.getProject();
        // TODO consulo: ProjectViewUpdateCause missing - ProjectView.refresh(ProjectViewUpdateCause.PLUGIN_PYTHON)
        ProjectView.getInstance(project).refresh();
        PsiManager.getInstance(project).dropPsiCaches();
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    public void resetAllNamespacePackages() {
        myNamespacePackageFolders.clear();
    }
}
