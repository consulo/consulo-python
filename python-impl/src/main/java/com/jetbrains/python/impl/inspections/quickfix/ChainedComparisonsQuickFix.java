// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.util.lang.ObjectUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User: catherine
 * <p>
 * QuickFix to replace chained comparisons with more simple version
 * For instance, a < b and b < c  --> a < b < c
 */
public class ChainedComparisonsQuickFix implements LocalQuickFix {

    private final boolean myCommonIsInLeftLeft;
    private final boolean myCommonIsInRightLeft;
    private final boolean myUseRightChildOfLeft;

    /**
     * @param commonIsInLeftLeft  true if common expression is on the left hand side of the left comparison
     * @param commonIsInRightLeft true if common expression is on the left hand side of the right comparison
     * @param useRightChildOfLeft whether left comparison is deeper in PSI tree than the right comparison.
     *                            E.g. in {@code foo and x > 1 and x < 3} expressions {@code x > 1} and {@code x < 3} are targets for simplification but
     *                            because of associativity of {@code and} operator they are not siblings: {@code (foo and x > 1) and x < 3}
     */
    public ChainedComparisonsQuickFix(boolean commonIsInLeftLeft, boolean commonIsInRightLeft, boolean useRightChildOfLeft) {
        myCommonIsInLeftLeft = commonIsInLeftLeft;
        myCommonIsInRightLeft = commonIsInRightLeft;
        myUseRightChildOfLeft = useRightChildOfLeft;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixChainedComparison();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyBinaryExpression expression = as(element, PyBinaryExpression.class);
        if (isLogicalAndExpression(expression)) {
            PyBinaryExpression rightExpression = as(expression.getRightExpression(), PyBinaryExpression.class);
            PyBinaryExpression leftExpression = as(expression.getLeftExpression(), PyBinaryExpression.class);
            if (isLogicalAndExpression(leftExpression)) {
                PyExpression nested = myUseRightChildOfLeft ? leftExpression.getRightExpression() : leftExpression.getLeftExpression();
                leftExpression = as(nested, PyBinaryExpression.class);
            }

            if (isComparisonExpression(leftExpression) && isComparisonExpression(rightExpression)) {
                applyFix(leftExpression, rightExpression, project);
            }
        }
    }

    private void applyFix(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression, Project project) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression newLeftExpression, newRightExpression;
        String operator;
        if (myCommonIsInLeftLeft) {
            newLeftExpression = invertExpression(leftExpression, elementGenerator);
        }
        else {
            newLeftExpression = leftExpression;
        }

        if (myCommonIsInRightLeft) {
            operator = getLeftestOperator(rightExpression).getText();
            newRightExpression = getLargeRightExpression(rightExpression, project);
        }
        else {
            operator = invertOperator(Objects.requireNonNull(rightExpression.getPsiOperator()));
            PyExpression rightLeftExpr = rightExpression.getLeftExpression();
            if (rightLeftExpr instanceof PyBinaryExpression) {
                newRightExpression = invertExpression((PyBinaryExpression)rightLeftExpr, elementGenerator);
            }
            else {
                newRightExpression = rightLeftExpr;
            }
        }
        PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(operator, newLeftExpression, newRightExpression);
        leftExpression.replace(makePsiConsistentBinaryExpression(project, binaryExpression));
        rightExpression.delete();
    }

    private static PyExpression makePsiConsistentBinaryExpression(Project project,
            PyBinaryExpression binaryExpression) {
        ArrayList<PyExpression> elements = new ArrayList<>();
        ArrayList<String> operators = new ArrayList<>();
        collectExpressionsDfs(elements, operators, binaryExpression);
        PyExpression resultExpression = buildResultExpression(project, elements, operators);
        return ObjectUtil.chooseNotNull(resultExpression, binaryExpression);
    }

    private static void collectExpressionsDfs(ArrayList<PyExpression> elements,
            ArrayList<String> operators,
            PyBinaryExpression expression) {
        PyExpression rightExpr = expression.getRightExpression();
        if (rightExpr instanceof PyBinaryExpression && isComparisonExpression(rightExpr)) {
            collectExpressionsDfs(elements, operators, (PyBinaryExpression)rightExpr);
        }
        else {
            elements.add(rightExpr);
        }
        if (expression.getPsiOperator() != null) {
            operators.add(expression.getPsiOperator().getText());
        }
        PyExpression leftExpr = expression.getLeftExpression();
        if (leftExpr instanceof PyBinaryExpression && isComparisonExpression(leftExpr)) {
            collectExpressionsDfs(elements, operators, (PyBinaryExpression)leftExpr);
        }
        else {
            elements.add(leftExpr);
        }
    }

    private static @Nullable PyExpression buildResultExpression(Project project,
            ArrayList<PyExpression> elements,
            ArrayList<String> operators) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        int size = elements.size();
        if (elements.isEmpty()) {
            return null;
        }
        PyExpression currentResult = elements.get(size - 1);
        int i = size - 2;
        while (i >= 0) {
            PyExpression nextElement = elements.get(i);
            String nextOperator = operators.get(i);
            currentResult = elementGenerator.createBinaryExpression(nextOperator, currentResult, nextElement);
            i--;
        }
        return currentResult;
    }

    private static PsiElement getLeftestOperator(PyBinaryExpression expression) {
        PsiElement op = expression.getPsiOperator();
        while (isComparisonExpression(expression.getLeftExpression())) {
            expression = (PyBinaryExpression)expression.getLeftExpression();
            op = expression.getPsiOperator();
        }
        assert op != null;
        return op;
    }

    private static PyExpression invertExpression(PyBinaryExpression expression,
            PyElementGenerator elementGenerator) {
        if (isComparisonExpression(expression)) {
            PyExpression left = expression.getLeftExpression();
            PyExpression right = expression.getRightExpression();

            String newOperator = invertOperator(Objects.requireNonNull(expression.getPsiOperator()));
            PyExpression newRight = isComparisonExpression(left) ? invertExpression((PyBinaryExpression)left, elementGenerator) : left;

            return elementGenerator.createBinaryExpression(newOperator, right, newRight);
        }
        else {
            return expression;
        }
    }

    private static String invertOperator(PsiElement op) {
        if (op.getText().equals(">")) {
            return "<";
        }
        if (op.getText().equals("<")) {
            return ">";
        }
        if (op.getText().equals(">=")) {
            return "<=";
        }
        if (op.getText().equals("<=")) {
            return ">=";
        }
        return op.getText();
    }

    private static @Nullable PyExpression getLargeRightExpression(PyBinaryExpression expression, Project project) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression left = expression.getLeftExpression();
        PyExpression right = expression.getRightExpression();
        PsiElement operator = expression.getPsiOperator();
        while (isComparisonExpression(left)) {
            assert operator != null;
            right = elementGenerator.createBinaryExpression(operator.getText(), ((PyBinaryExpression)left).getRightExpression(), right);
            operator = ((PyBinaryExpression)left).getPsiOperator();
            left = ((PyBinaryExpression)left).getLeftExpression();
        }
        return right;
    }

    private static boolean isComparisonExpression(@Nullable PyExpression expression) {
        if (!(expression instanceof PyBinaryExpression)) {
            return false;
        }
        PyElementType operator = ((PyBinaryExpression)expression).getOperator();
        return PyTokenTypes.RELATIONAL_OPERATIONS.contains(operator) || PyTokenTypes.EQUALITY_OPERATIONS.contains(operator);
    }

    private static boolean isLogicalAndExpression(@Nullable PyExpression expression) {
        return expression instanceof PyBinaryExpression && ((PyBinaryExpression)expression).getOperator() == PyTokenTypes.AND_KEYWORD;
    }
}
