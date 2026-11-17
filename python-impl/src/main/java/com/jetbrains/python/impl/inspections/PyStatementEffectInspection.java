// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.PyStatementEffectQuickFixProvider;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.StatementEffectFunctionCallQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.StatementEffectIntroduceVariableQuickFix;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

@ExtensionImpl
public final class PyStatementEffectInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameStatementEffect();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonControlFlowIssues();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    @Override
    protected boolean isSuppressForCodeFragment() {
        return true;
    }

    public static class Visitor extends PyInspectionVisitor {

        public Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyExpressionStatement(PyExpressionStatement node) {
            if (ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                     extension -> extension.ignoreNoEffectStatement(node))) {
                return;
            }
            PyExpression expression = node.getExpression();
            if (PsiTreeUtil.hasErrorElements(expression)) {
                return;
            }
            if (hasEffect(expression)) {
                return;
            }

            // https://twitter.com/gvanrossum/status/112670605505077248
            if (expression instanceof PyStringLiteralExpression) {
                return;
            }

            PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
            if (tryPart != null) {
                PyStatementList statementList = tryPart.getStatementList();
                if (statementList.getStatements().length == 1 && statementList.getStatements()[0] == node) {
                    return;
                }
            }
            List<LocalQuickFix> quickFixes = new SmartList<>(getQuickFixesFromExtensions(node));
            if (!(expression instanceof PyReferenceExpression reference) || reference.isQualified()) {
                quickFixes.add(new StatementEffectIntroduceVariableQuickFix());
            }
            registerProblem(expression,
                            PyLocalize.inspStatementEffectStatementSeemsToHaveNoEffect().get(),
                            quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }

        private boolean hasEffect(@Nullable PyExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PyCallExpression || expression instanceof PyYieldExpression) {
                return true;
            }
            else if (expression instanceof PyListCompExpression) {
                if (hasEffect(((PyListCompExpression)expression).getResultExpression())) {
                    return true;
                }
            }
            else if (expression instanceof PyBinaryExpression binary) {

                PyElementType operator = binary.getOperator();
                if (PyTokenTypes.COMPARISON_OPERATIONS.contains(operator)) {
                    return false;
                }

                PyExpression leftExpression = binary.getLeftExpression();
                PyExpression rightExpression = binary.getRightExpression();
                if (hasEffect(leftExpression) || hasEffect(rightExpression)) {
                    return true;
                }

                String method = operator == null ? null : operator.getSpecialMethodName();
                if (method != null) {
                    // maybe the op is overridden and may produce side effects, like cout << "hello"
                    PyType type = myTypeEvalContext.getType(leftExpression);
                    if (!isUnknown(type) &&
                        !type.isBuiltin() &&
                        type.resolveMember(method, null, AccessDirection.READ, getResolveContext()) != null) {
                        return true;
                    }
                    if (rightExpression != null) {
                        type = myTypeEvalContext.getType(rightExpression);
                        if (!isUnknown(type)) {
                            String rmethod = "__r" + method.substring(2); // __add_ -> __radd__
                            if (!type.isBuiltin() && type.resolveMember(rmethod, null, AccessDirection.READ, getResolveContext()) != null) {
                                return true;
                            }
                        }
                    }
                }
            }
            else if (expression instanceof PyConditionalExpression conditionalExpression) {
                return hasEffect(conditionalExpression.getTruePart()) || hasEffect(conditionalExpression.getFalsePart());
            }
            else if (expression instanceof PyParenthesizedExpression parenthesizedExpression) {
                return hasEffect(parenthesizedExpression.getContainedExpression());
            }
            else if (expression instanceof PyReferenceExpression referenceExpression) {
                ResolveResult[] results = referenceExpression.getReference(getResolveContext()).multiResolve(true);
                for (ResolveResult res : results) {
                    if (res.getElement() instanceof PyFunction) {
                        registerProblem(expression,
                                        PyLocalize.inspStatementEffectStatementHavingNoEffectCanBeReplacedWithFunctionCall().get(),
                                        new StatementEffectFunctionCallQuickFix());
                        return true;
                    }
                }
            }
            else if (expression instanceof PyTupleExpression) {
                PyExpression[] elements = ((PyTupleExpression)expression).getElements();
                for (PyExpression element : elements) {
                    if (hasEffect(element)) {
                        return true;
                    }
                }
            }
            else if (expression instanceof PyPrefixExpression prefixExpr) {
                return prefixExpr.getOperator() == PyTokenTypes.AWAIT_KEYWORD;
            }
            else if (expression instanceof PyEllipsisLiteralExpression) {
                return true;
            }
            return false;
        }
    }

    private static List<LocalQuickFix> getQuickFixesFromExtensions(PyExpressionStatement expressionStatement) {
        return ContainerUtil.mapNotNull(PyStatementEffectQuickFixProvider.EP_NAME.getExtensionList(),
                                        extension -> extension.getQuickFix(expressionStatement));
    }
}
