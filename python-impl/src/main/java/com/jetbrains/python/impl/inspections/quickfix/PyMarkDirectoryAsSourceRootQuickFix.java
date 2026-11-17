// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.module.PySourceRootDetectionService;
import consulo.component.util.Iconable;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import java.io.File;

public class PyMarkDirectoryAsSourceRootQuickFix implements LocalQuickFix, Iconable {
    private final Project myProject;
    private final VirtualFile mySourceRoot;

    public PyMarkDirectoryAsSourceRootQuickFix(Project project, VirtualFile sourceRoot) {
        myProject = project;
        mySourceRoot = sourceRoot;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    // JB getFamilyName(): PyPsiBundle.message("QFIX.add.source.root.for.unresolved.import.family.name")
    // (Consulo LocalQuickFix has only getName(); the family name is PyLocalize.qfixAddSourceRootForUnresolvedImportFamilyName())

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddSourceRootForUnresolvedImportName(getPathName());
    }

    private String getPathName() {
        // TODO consulo: Project.guessProjectDir missing, Project.getBaseDir used instead
        VirtualFile projectDir = myProject.getBaseDir();
        if (projectDir == null) {
            return mySourceRoot.getName();
        }
        String relativePath = VirtualFileUtil.getRelativePath(mySourceRoot, projectDir, File.separatorChar);
        return relativePath != null ? relativePath.replace("\\", "/") : mySourceRoot.getName();
    }

    @Override
    public Image getIcon(int flags) {
        return PlatformIconGroup.modulesSourceroot();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PySourceRootDetectionService.getInstance(project).markAsSourceRoot(mySourceRoot, true);
    }
}
