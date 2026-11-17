// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.Objects;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class SimplifyBooleanCheckQuickFix implements LocalQuickFix {
    private final String myReplacementText;

    public SimplifyBooleanCheckQuickFix(PyBinaryExpression binaryExpression) {
        myReplacementText = createReplacementText(binaryExpression);
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixSimplifyBooleanExpression(myReplacementText);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyPsiUtils.assertValid(element);
        if (!element.isValid() || !(element instanceof PyBinaryExpression)) {
            return;
        }
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), myReplacementText));
    }

    private static boolean isTrue(PyExpression expression) {
        return "True".equals(expression.getText());
    }

    private static boolean isFalse(PyExpression expression) {
        return "False".equals(expression.getText());
    }

    private static boolean isFalsey(PyExpression expression) {
        return isFalse(expression) || isZero(expression) || isEmpty(expression);
    }

    private static boolean isZero(PyExpression expression) {
        return "0".equals(expression.getText());
    }

    private static boolean isEmpty(PyExpression expression) {
        return "[]".equals(expression.getText());
    }

    private static String createReplacementText(PyBinaryExpression binaryExpression) {
        PyExpression leftExpression = binaryExpression.getLeftExpression();
        PyExpression rightExpression = Objects.requireNonNull(binaryExpression.getRightExpression());
        boolean positiveCondition =
            (binaryExpression.getOperator() == PyTokenTypes.EQEQ || binaryExpression.isOperator(PyNames.IS))
                ^ (isFalsey(leftExpression) || isFalsey(rightExpression));
        PyExpression resultExpression = isTrue(leftExpression) || isFalsey(leftExpression)
            ? rightExpression
            : leftExpression;
        return (positiveCondition ? "" : "not ") + resultExpression.getText();
    }
}
