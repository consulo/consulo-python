// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class ReplaceNotEqOperatorQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.intnReplaceNoteqOperator();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();

        if (element instanceof PyBinaryExpression) {
            PsiElement operator = ((PyBinaryExpression)element).getPsiOperator();
            if (operator != null) {
                PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
                operator.replace(elementGenerator.createFromText(LanguageLevel.forElement(element), LeafPsiElement.class, "!="));
            }
        }
    }
}
