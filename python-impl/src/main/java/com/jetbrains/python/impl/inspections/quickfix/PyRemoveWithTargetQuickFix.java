// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithItem;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyRemoveWithTargetQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveWithTarget();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        final PyWithItem withItem = PsiTreeUtil.getParentOfType(element, PyWithItem.class);
        if (withItem == null) {
            return;
        }
        final PyExpression withTarget = withItem.getTarget();
        if (withTarget != element) {
            return;
        }
        final PyExpression withExpr = withItem.getExpression();
        withItem.deleteChildRange(withExpr.getNextSibling(), withItem.getLastChild());
    }
}
