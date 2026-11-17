// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class RemoveTrailingSuffixQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRemoveTrailingSuffix();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyNumericLiteralExpression numeric) {
            String suffix = numeric.getIntegerLiteralSuffix();
            if (suffix == null) {
                return;
            }
            String text = numeric.getText();
            String newText = text.substring(0, text.length() - suffix.length());
            numeric.replace(
                PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel.forElement(numeric), newText));
        }
    }
}
