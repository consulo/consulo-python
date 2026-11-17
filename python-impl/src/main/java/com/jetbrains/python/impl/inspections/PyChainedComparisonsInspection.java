// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.ChainedComparisonsQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.UpdateInspectionOptionFix;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User: catherine
 * <p>
 * Inspection to detect chained comparisons which can be simplified
 * For instance, a < b and b < c  -->  a < b < c
 */
@ExtensionImpl
public final class PyChainedComparisonsInspection extends PyInspection {

    private static final String INSPECTION_SHORT_NAME = "PyChainedComparisonsInspection";

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameChainedComparisons();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyChainedComparisonsInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        PyChainedComparisonsInspectionState inspectionState = (PyChainedComparisonsInspectionState) state;
        return new Visitor(holder, inspectionState.ignoreConstantInTheMiddle, PyInspectionVisitor.getContext(session));
    }

    private class Visitor extends PyInspectionVisitor {
        /**
         * @see ChainedComparisonsQuickFix#ChainedComparisonsQuickFix(boolean, boolean, boolean)
         */
        boolean myIsLeft;
        boolean myIsRight;
        @Nullable PyElementType myOperator;
        boolean getInnerRight;
        boolean isConstantInTheMiddle;
        boolean ignoreConstantInTheMiddle;

        Visitor(@Nullable ProblemsHolder holder,
                boolean ignoreConstantInTheMiddle,
                TypeEvalContext context) {
            super(holder, context);
            this.ignoreConstantInTheMiddle = ignoreConstantInTheMiddle;
        }

        @Override
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            myIsLeft = false;
            myIsRight = false;
            myOperator = null;
            getInnerRight = false;

            PyBinaryExpression leftExpression = as(node.getLeftExpression(), PyBinaryExpression.class);
            PyBinaryExpression rightExpression = as(node.getRightExpression(), PyBinaryExpression.class);

            if (leftExpression != null && rightExpression != null && node.getOperator() == PyTokenTypes.AND_KEYWORD) {
                boolean applicable = false;
                if (leftExpression.getOperator() == PyTokenTypes.AND_KEYWORD) {
                    PyBinaryExpression leftLeft = as(leftExpression.getLeftExpression(), PyBinaryExpression.class);
                    PyBinaryExpression leftRight = as(leftExpression.getRightExpression(), PyBinaryExpression.class);
                    if (leftLeft != null && (isRightSimplified(leftLeft, rightExpression) || isLeftSimplified(leftLeft, rightExpression))) {
                        applicable = true;
                        getInnerRight = false;
                    }
                    else if (leftRight != null && (isRightSimplified(leftRight, rightExpression) || isLeftSimplified(leftRight, rightExpression))) {
                        applicable = true;
                        getInnerRight = true;
                    }
                }
                else if (isRightSimplified(leftExpression, rightExpression) || isLeftSimplified(leftExpression, rightExpression)) {
                    applicable = true;
                }

                if (applicable) {
                    if (isConstantInTheMiddle) {
                        if (!ignoreConstantInTheMiddle) {
                            registerProblem(node, PyLocalize.inspSimplifyChainedComparison().get(),
                                            new ChainedComparisonsQuickFix(myIsLeft, myIsRight, getInnerRight),
                                            new UpdateInspectionOptionFix<PyChainedComparisonsInspection, PyChainedComparisonsInspectionState>(
                                                PyChainedComparisonsInspection.this,
                                                PyLocalize.inspChainedComparisonsIgnoreStatementsWithConstantInTheMiddle(),
                                                s -> s.ignoreConstantInTheMiddle = true));
                        }
                    }
                    else {
                        registerProblem(node, PyLocalize.inspSimplifyChainedComparison().get(),
                                        new ChainedComparisonsQuickFix(myIsLeft, myIsRight, getInnerRight));
                    }
                }
            }
        }

        private boolean isRightSimplified(PyBinaryExpression leftExpression,
                                          PyBinaryExpression rightExpression) {
            PyExpression leftRight = leftExpression.getRightExpression();
            if (leftRight instanceof PyBinaryExpression &&
                PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression) leftRight).getOperator())) {
                if (isRightSimplified((PyBinaryExpression) leftRight, rightExpression)) {
                    return true;
                }
            }

            myOperator = leftExpression.getOperator();
            if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(myOperator)) {
                if (leftRight != null) {
                    if (leftRight.getText().equals(getLeftExpression(rightExpression, true).getText())) {
                        myIsLeft = false;
                        myIsRight = true;
                        isConstantInTheMiddle = leftRight instanceof PyLiteralExpression;
                        return true;
                    }

                    PyExpression right = getSmallestRight(rightExpression, true);
                    if (right != null && leftRight.getText().equals(right.getText())) {
                        myIsLeft = false;
                        myIsRight = false;
                        isConstantInTheMiddle = leftRight instanceof PyLiteralExpression;
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean isOpposite(@Nullable PyElementType op1, @Nullable PyElementType op2) {
            if ((op1 == PyTokenTypes.GT || op1 == PyTokenTypes.GE) && (op2 == PyTokenTypes.LT || op2 == PyTokenTypes.LE)) {
                return true;
            }
            if ((op2 == PyTokenTypes.GT || op2 == PyTokenTypes.GE) && (op1 == PyTokenTypes.LT || op1 == PyTokenTypes.LE)) {
                return true;
            }
            return false;
        }


        private boolean isLeftSimplified(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression) {
            PyExpression leftLeft = leftExpression.getLeftExpression();
            if (leftLeft instanceof PyBinaryExpression &&
                PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression) leftLeft).getOperator())) {
                if (isLeftSimplified((PyBinaryExpression) leftLeft, rightExpression)) {
                    return true;
                }
            }

            myOperator = leftExpression.getOperator();
            if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(myOperator)) {
                if (leftLeft != null) {
                    if (leftLeft.getText().equals(getLeftExpression(rightExpression, false).getText())) {
                        myIsLeft = true;
                        myIsRight = true;
                        isConstantInTheMiddle = leftLeft instanceof PyLiteralExpression;
                        return true;
                    }
                    PyExpression right = getSmallestRight(rightExpression, false);
                    if (right != null && leftLeft.getText().equals(right.getText())) {
                        myIsLeft = true;
                        myIsRight = false;
                        isConstantInTheMiddle = leftLeft instanceof PyLiteralExpression;
                        return true;
                    }
                }
            }
            return false;
        }

        private PyExpression getLeftExpression(PyBinaryExpression expression, boolean isRight) {
            PyExpression result = expression;
            while (result instanceof PyBinaryExpression &&
                (PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression) result).getOperator()) ||
                    PyTokenTypes.EQUALITY_OPERATIONS.contains(((PyBinaryExpression) result).getOperator()))) {

                boolean opposite = isOpposite(((PyBinaryExpression) result).getOperator(), myOperator);
                if ((isRight && opposite) || (!isRight && !opposite)) {
                    break;
                }
                result = ((PyBinaryExpression) result).getLeftExpression();
            }
            return result;
        }

        private @Nullable PyExpression getSmallestRight(PyBinaryExpression expression, boolean isRight) {
            PyExpression result = expression;
            while (result instanceof PyBinaryExpression &&
                (PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression) result).getOperator()) ||
                    PyTokenTypes.EQUALITY_OPERATIONS.contains(((PyBinaryExpression) result).getOperator()))) {

                boolean opposite = isOpposite(((PyBinaryExpression) result).getOperator(), myOperator);
                if ((isRight && !opposite) || (!isRight && opposite)) {
                    break;
                }
                result = ((PyBinaryExpression) result).getRightExpression();
            }
            return result;
        }
    }
}
