// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveCallQuickFix;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

@ExtensionImpl
public class PyCallingNonCallableInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameCallingNonCallable();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            super.visitPyCallExpression(node);
            checkCallable(node, node.getCallee());
        }

        @Override
        public void visitPyDecorator(PyDecorator decorator) {
            super.visitPyDecorator(decorator);
            checkCallable(decorator, decorator.getCallee());
            if (decorator.hasArgumentList()) {
                checkCallable(decorator, decorator);
            }
        }

        private void checkCallable(PyElement node, @Nullable PyExpression callee) {
            if (node.getParent() instanceof PyDecorator) {
                return; // we've already been here
            }
            if (callee == null) {
                return;
            }

            if (!Objects.equals(isCallable(callee, myTypeEvalContext), false)) {
                return;
            }
            PyType calleeType = myTypeEvalContext.getType(callee);
            if (calleeType instanceof PyUnionType unionType && PyUnionType.isStrictSemanticsEnabled()) {
                PyType uncallable = PyUnionType.unionOrUnknown(
                    ContainerUtil.filter(unionType.getMembers(), it -> Objects.equals(PyTypeChecker.isCallable(it), false)));
                ProblemMessage message = uncallable instanceof PyUnionType
                    ? PyInspectionMessages.problemMessage(args -> PyLocalize.inspMembersAreNotCallable(args[0], args[1]),
                                                          CodifiedParam.ofType(uncallable, callee, myTypeEvalContext),
                                                          CodifiedParam.ofType(calleeType, callee, myTypeEvalContext))
                    : PyInspectionMessages.problemMessage(args -> PyLocalize.inspMemberIsNotCallable(args[0], args[1]),
                                                          CodifiedParam.ofType(uncallable, callee, myTypeEvalContext),
                                                          CodifiedParam.ofType(calleeType, callee, myTypeEvalContext));
                registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new PyRemoveCallQuickFix());
                return;
            }
            ProblemMessage message;
            if (calleeType instanceof PyClassType) {
                message = PyInspectionMessages.problemMessage(args -> PyLocalize.inspClassObjectIsNotCallable(args[0]), calleeType.getName());
            }
            else if (callee.getName() != null) {
                message = PyInspectionMessages.problemMessage(args -> PyLocalize.inspSymbolIsNotCallable(args[0]), callee.getName());
            }
            else {
                message = PyInspectionMessages.problemMessage(args -> PyLocalize.inspExpressionIsNotCallable());
            }
            registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new PyRemoveCallQuickFix());
        }
    }

    private static @Nullable Boolean isCallable(PyExpression expression, TypeEvalContext context) {
        if (expression instanceof PyQualifiedExpression && PyNames.__CLASS__.equals(expression.getName())) {
            return true;
        }

        if (expression instanceof PyReferenceExpression referenceExpression) {
            // PEP 747: `TypeForm(...)` is callable, even though the declared type of `TypeForm` is the non-callable `_SpecialForm`.
            Collection<String> qNames = PyTypingTypeProvider.resolveToQualifiedNames(referenceExpression, context);
            if (qNames.contains(PyTypingTypeProvider.TYPE_FORM) || qNames.contains(PyTypingTypeProvider.TYPE_FORM_EXT)) {
                return true;
            }
            PsiElement resolved = referenceExpression.getReference(PyResolveContext.defaultContext(context)).resolve();
            if (resolved instanceof PyTargetExpression targetExpression) {
                if (isExplicitTypeAliasWithNonCallableValue(targetExpression, context)) {
                    return false;
                }
                // TODO: Handle implicit aliases
            }
        }

        return PyTypeChecker.isCallable(context.getType(expression));
    }

    private static boolean isExplicitTypeAliasWithNonCallableValue(PyTargetExpression target, TypeEvalContext context) {
        if (!PyTypingTypeProvider.isExplicitTypeAlias(target, context)) {
            return false;
        }

        PyExpression aliasExpr = target.findAssignedValue();
        if (aliasExpr == null) {
            return false;
        }

        // Union type cannot be instantiated: https://docs.python.org/3/library/typing.html#typing.Union
        return context.getType(aliasExpr) instanceof PyUnionType;
    }
}
