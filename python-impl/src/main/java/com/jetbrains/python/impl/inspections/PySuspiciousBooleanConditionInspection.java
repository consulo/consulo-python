// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssertStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyWhileStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PySuspiciousBooleanConditionInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameSuspiciousBooleanCondition();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonAsynchronousCode();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyIfStatement(PyIfStatement node) {
            checkBooleanContext(node.getIfPart().getCondition());
            for (PyIfPart elifPart : node.getElifParts()) {
                checkBooleanContext(elifPart.getCondition());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyWhileStatement(PyWhileStatement node) {
            checkBooleanContext(node.getWhilePart().getCondition());
        }

        @Override
        @RequiredReadAction
        public void visitPyConditionalExpression(PyConditionalExpression node) {
            checkBooleanContext(node.getCondition());
        }

        @Override
        @RequiredReadAction
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            if (node.isShortCircuit()) {
                checkBooleanContext(node.getLeftExpression());
                checkBooleanContext(node.getRightExpression());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyPrefixExpression(PyPrefixExpression node) {
            if (node.getOperator() == PyTokenTypes.NOT_KEYWORD) {
                checkBooleanContext(node.getOperand());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyAssertStatement(PyAssertStatement node) {
            PyExpression[] arguments = node.getArguments();
            if (arguments.length > 0) {
                checkBooleanContext(arguments[0]);
            }
        }

        @RequiredReadAction
        private void checkBooleanContext(@Nullable PyExpression expression) {
            if (expression == null) {
                return;
            }
            PyExpression node = PyPsiUtils.flattenParens(expression);
            if (node instanceof PyBinaryExpression binaryExpression && binaryExpression.isShortCircuit()) {
                return;
            }
            checkExpression(expression);
        }

        @RequiredReadAction
        private void checkExpression(@Nullable PyExpression expression) {
            if (expression == null) {
                return;
            }

            // Don't flag awaited expressions - they're already being awaited
            if (expression instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
                return;
            }

            PyType type = myTypeEvalContext.getType(expression);
            if (type == null) {
                return;
            }

            // Check if the type is a coroutine
            if (type instanceof PyClassType classType &&
                (PyTypingTypeProvider.COROUTINE.equals(classType.getClassQName()) ||
                    PyNames.TYPES_COROUTINE_TYPE.equals(classType.getClassQName()))) {
                registerProblem(expression, PyLocalize.inspSuspiciousBooleanConditionCoroutine().get(), new PyAddAwaitQuickFix());
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class PyAddAwaitQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixAddAwait();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyExpression element)) {
                return;
            }

            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            PyExpression awaitExpr = generator.createExpressionFromText(
                LanguageLevel.forElement(element),
                PyNames.AWAIT + " " + element.getText()
            );
            element.replace(awaitExpr);
        }
    }
}
