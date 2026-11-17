// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * @author Alexey.Ivanov
 * @since 2010-03-24
 */
public class ComparisonWithNoneQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixReplaceEquality();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (descriptor.getPsiElement() instanceof PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            String temp;
            temp = (operator == PyTokenTypes.EQEQ) ? "is" : "is not";
            PyExpression expression = elementGenerator.createBinaryExpression(
                temp,
                binaryExpression.getLeftExpression(),
                binaryExpression.getRightExpression()
            );
            binaryExpression.replace(expression);
        }
    }
}
