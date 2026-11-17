// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

import java.util.List;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyConvertToRawStringQuickFix implements LocalQuickFix, LowPriorityAction {
    private final int myOffsetInElement;

    public PyConvertToRawStringQuickFix(int offsetInElement) {
        myOffsetInElement = offsetInElement;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixConvertToRawString();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PyStringLiteralExpression stringLiteral)) {
            return;
        }

        PyStringElement part = null;
        for (PyStringElement it : stringLiteral.getStringElements()) {
            if (it.getTextRangeInParent().contains(myOffsetInElement)) {
                part = it;
                break;
            }
        }
        if (part == null) {
            return;
        }
        if (StringUtil.containsIgnoreCase(part.getPrefix(), "r")) {
            return;
        }

        String newText = "r" + part.getText();

        List<PyStringElement> newParts = PyElementGenerator.getInstance(project)
            .createStringLiteralAlreadyEscaped(newText)
            .getStringElements();
        if (newParts.isEmpty()) {
            return;
        }
        part.replace(newParts.get(0));
    }
}
