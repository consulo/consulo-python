// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStarExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyReplaceStarByUnpackQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixReplaceStarByUnpack();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PyStarExpression starExpression)) {
            return;
        }

        PyExpression expression = starExpression.getExpression();
        if (expression == null) {
            return;
        }
        PsiFile file = starExpression.getContainingFile();

        PyUnpackTypeVarTupleQuickFix.replaceToTypingExtensionsUnpack(starExpression, expression, file, project);
    }
}
