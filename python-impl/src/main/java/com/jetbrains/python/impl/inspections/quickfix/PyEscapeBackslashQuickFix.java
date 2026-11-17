// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyEscapeBackslashQuickFix implements LocalQuickFix {
    private final int myOffsetInElement;

    public PyEscapeBackslashQuickFix(int offsetInElement) {
        myOffsetInElement = offsetInElement;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixEscapeBackslash();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PyStringLiteralExpression)) {
            return;
        }

        if (myOffsetInElement >= element.getTextLength() || element.getText().charAt(myOffsetInElement) != '\\') {
            return;
        }

        String newText = new StringBuilder(element.getText()).insert(myOffsetInElement, "\\").toString();

        PyStringLiteralExpression newElement = PyElementGenerator.getInstance(project)
            .createStringLiteralAlreadyEscaped(newText);
        element.replace(newElement);
    }
}
