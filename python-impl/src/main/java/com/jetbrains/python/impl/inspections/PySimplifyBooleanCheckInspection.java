// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.SimplifyBooleanCheckQuickFix;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

@ExtensionImpl
public class PySimplifyBooleanCheckInspection extends PyInspection {
    private static final List<String> COMPARISON_LITERALS = List.of("True", "False", "[]");

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameCheckCanBeSimplified();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonControlFlowIssues();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    /**
     * JB option {@code ignoreComparisonToZero} lives in {@link PySimplifyBooleanCheckInspectionState}.
     */
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PySimplifyBooleanCheckInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        PySimplifyBooleanCheckInspectionState inspectionState = (PySimplifyBooleanCheckInspectionState) state;
        return new Visitor(holder, inspectionState.ignoreComparisonToZero, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        private final boolean myIgnoreComparisonToZero;

        Visitor(@Nullable ProblemsHolder holder, boolean ignoreComparisonToZero, TypeEvalContext context) {
            super(holder, context);
            myIgnoreComparisonToZero = ignoreComparisonToZero;
        }

        @Override
        public void visitPyConditionalStatementPart(PyConditionalStatementPart node) {
            super.visitPyConditionalStatementPart(node);
            PyExpression condition = node.getCondition();
            if (condition != null) {
                condition.accept(new PyBinaryExpressionVisitor(getHolder(), myTypeEvalContext, myIgnoreComparisonToZero));
            }
        }
    }

    private static class PyBinaryExpressionVisitor extends PyInspectionVisitor {
        private final boolean myIgnoreComparisonToZero;

        PyBinaryExpressionVisitor(@Nullable ProblemsHolder holder, TypeEvalContext context, boolean ignoreComparisonToZero) {
            super(holder, context);
            myIgnoreComparisonToZero = ignoreComparisonToZero;
        }

        @Override
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            super.visitPyBinaryExpression(node);
            PyElementType operator = node.getOperator();
            PyExpression leftExpression = node.getLeftExpression();
            PyExpression rightExpression = node.getRightExpression();
            if (rightExpression == null || rightExpression instanceof PyBinaryExpression ||
                leftExpression instanceof PyBinaryExpression) {
                return;
            }

            PyType leftType = myTypeEvalContext.getType(leftExpression);
            PyType rightType = myTypeEvalContext.getType(rightExpression);

            boolean isIdentity = node.isOperator(PyNames.IS) || node.isOperator("isnot");

            // if no type and `is`, then it's unsafe
            if ((isAnyOrUnknown(leftType) || isAnyOrUnknown(rightType)) && isIdentity) {
                return;
            }

            // because we are comparing to literal values, there will only ever be a union on one side
            Collection<@Nullable PyType> unionMembers = leftType instanceof PyUnionType leftUnion
                ? leftUnion.getMembers()
                : rightType instanceof PyUnionType rightUnion ? rightUnion.getMembers() : null;

            boolean isOptional = unionMembers != null && unionMembers.stream().anyMatch(it -> isNoneType(it));

            // if the union is `X | Y | None` or just `X | Y` then it is unsafe to simplify
            if (isOptional && unionMembers.size() > 2 || !isOptional && unionMembers != null) {
                return;
            }

            if (!isIdentity && !PyTokenTypes.EQUALITY_OPERATIONS.contains(operator)) {
                return;
            }

            boolean compareWithZero = !myIgnoreComparisonToZero && operandsEqualTo(node, Set.of("0"));
            boolean compareWithFalsey = operandsEqualTo(node, List.of(PyNames.FALSE, "[]"));

            // 'x is falsey' where `x` is `T | None`, then it is unsafe to simplify
            //  because the falsey value will evaluate to `False` which will be ambiguous with `None`
            if (isOptional && (compareWithFalsey || compareWithZero)) {
                return;
            }

            if (operandsEqualTo(node, COMPARISON_LITERALS) || compareWithZero) {
                registerProblem(node);
            }
        }

        public void registerProblem(PyBinaryExpression binaryExpression) {
            registerProblem(
                binaryExpression,
                PyLocalize.inspExpressionCanBeSimplified().get(),
                new SimplifyBooleanCheckQuickFix(binaryExpression)
            );
        }

        private static boolean operandsEqualTo(PyBinaryExpression expr, Collection<String> literals) {
            String leftExpressionText = expr.getLeftExpression().getText();
            PyExpression rightExpression = expr.getRightExpression();
            String rightExpressionText = rightExpression != null ? rightExpression.getText() : null;
            for (String literal : literals) {
                if (literal.equals(leftExpressionText) || literal.equals(rightExpressionText)) {
                    return true;
                }
            }
            return false;
        }
    }
}
