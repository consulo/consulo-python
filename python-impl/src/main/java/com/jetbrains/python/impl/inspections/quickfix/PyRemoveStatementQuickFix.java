// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyStatement;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyRemoveStatementQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveStatement();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyStatement statement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyStatement.class, false);
        if (statement != null) {
            statement.delete();
        }
    }
}
