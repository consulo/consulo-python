// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

//TODO: Remove pydoc aswell
public class PyRemoveArgumentQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveArgument();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PyExpression expression)) {
            return;
        }
        PsiElement nextSibling = PsiTreeUtil.skipWhitespacesForward(expression);
        PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(expression);
        expression.delete();
        if (nextSibling != null && nextSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
            nextSibling.delete();
            return;
        }
        if (prevSibling != null && prevSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
            prevSibling.delete();
        }
    }
}
