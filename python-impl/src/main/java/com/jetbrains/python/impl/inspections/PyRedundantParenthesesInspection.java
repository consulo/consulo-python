// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyPrintStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyWhilePart;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;

/**
 * User: catherine
 * <p>
 * Inspection to detect redundant parentheses in if/while statement.
 */
@ExtensionImpl
public final class PyRedundantParenthesesInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameRedundantParentheses();
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
        return new PyRedundantParenthesesInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), (PyRedundantParenthesesInspectionState) state);
    }

    private static boolean isTupleWithUnpacking(PyExpression expression) {
        return expression instanceof PyTupleExpression &&
               ContainerUtil.or(((PyTupleExpression)expression).getElements(), PyStarExpression.class::isInstance);
    }

    private static boolean oneElementTuple(PyExpression expression) {
        return expression instanceof PyTupleExpression && ((PyTupleExpression)expression).getElements().length == 1;
    }

    private static boolean isYieldFrom(PsiElement element) {
        return element instanceof PyYieldExpression && ((PyYieldExpression)element).isDelegating();
    }

    private static class Visitor extends PyInspectionVisitor {
        private final PyRedundantParenthesesInspectionState myState;

        Visitor(ProblemsHolder holder, TypeEvalContext context, PyRedundantParenthesesInspectionState state) {
            super(holder, context);
            myState = state;
        }

        @Override
        public void visitPyParenthesizedExpression(PyParenthesizedExpression node) {
            if (node.textContains('\n')) {
                return;
            }
            PsiElement parent = node.getParent();
            if (parent instanceof PyParenthesizedExpression) {
                return;
            }
            PyExpression expression = node.getContainedExpression();
            if (expression == null || expression instanceof PyYieldExpression) {
                return;
            }
            if (expression instanceof PyTupleExpression && myState.myIgnoreTupleInReturn) {
                return;
            }
            LanguageLevel languageLevel = LanguageLevel.forElement(node);
            if (expression instanceof PyReferenceExpression || expression instanceof PyLiteralExpression) {
                if (myState.myIgnorePercOperator) {
                    if (parent instanceof PyBinaryExpression) {
                        if (((PyBinaryExpression)parent).getOperator() == PyTokenTypes.PERC) {
                            return;
                        }
                    }
                }

                if (expression instanceof PyNumericLiteralExpression &&
                    ((PyNumericLiteralExpression)expression).isIntegerLiteral() &&
                    parent instanceof PyReferenceExpression) {
                    return;
                }

                if (parent instanceof PyPrintStatement) {
                    return;
                }
                registerProblem(node, PyLocalize.qfixRedundantParentheses().get(), new RedundantParenthesesQuickFix());
            }
            else if (parent instanceof PyIfPart ||
                     parent instanceof PyWhilePart) {
                registerProblem(node, PyLocalize.qfixRedundantParentheses().get(), new RedundantParenthesesQuickFix());
            }
            else if (parent instanceof PyReturnStatement || parent instanceof PyYieldExpression) {
                if (!(isTupleWithUnpacking(expression) && languageLevel.isOlderThan(LanguageLevel.PYTHON38)) && !oneElementTuple(expression) &&
                    !isYieldFrom(parent)) {
                    registerProblem(node, PyLocalize.qfixRedundantParentheses().get(), new RedundantParenthesesQuickFix());
                }
            }
            else if (expression instanceof PyBinaryExpression binaryExpression) {

                if (parent instanceof PyPrefixExpression) {
                    return;
                }
                if (binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD ||
                    binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
                    PyExpression leftExpression = binaryExpression.getLeftExpression();
                    PyExpression rightExpression = binaryExpression.getRightExpression();
                    if (leftExpression instanceof PyParenthesizedExpression && rightExpression instanceof PyParenthesizedExpression &&
                        !(((PyParenthesizedExpression)leftExpression).getContainedExpression() instanceof PyBinaryExpression) &&
                        !(((PyParenthesizedExpression)rightExpression).getContainedExpression() instanceof PyBinaryExpression)) {
                        registerProblem(node, PyLocalize.qfixRedundantParentheses().get(), new RedundantParenthesesQuickFix());
                    }
                }
            }
            else if (expression instanceof PyParenthesizedExpression) {
                registerProblem(expression, PyLocalize.qfixRedundantParentheses().get(), new RedundantParenthesesQuickFix());
            }
        }

        @Override
        public void visitPyArgumentList(PyArgumentList node) {
            if (!(node.getParent() instanceof PyClass)) {
                return;
            }
            if (!myState.myIgnoreEmptyBaseClasses && node.getArguments().length == 0) {
                registerProblem(node, PyLocalize.qfixRedundantParentheses().get(), new RedundantParenthesesQuickFix());
            }
        }
    }
}
