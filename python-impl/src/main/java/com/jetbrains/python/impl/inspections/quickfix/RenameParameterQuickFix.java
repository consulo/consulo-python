// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.psi.PyNamedParameter;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * Parameter renaming.
 */
public class RenameParameterQuickFix implements LocalQuickFix {
    private final String myNewName;

    public RenameParameterQuickFix(String newName) {
        myNewName = newName;
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyNamedParameter pyNamedParameter) {
            // Consulo: no ModPsiUpdater.rename, the rename processor is run instead
            PythonUiService.getInstance().runRenameProcessor(project, pyNamedParameter, myNewName, false, true);
        }
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRenameParameter(myNewName);
    }
}
