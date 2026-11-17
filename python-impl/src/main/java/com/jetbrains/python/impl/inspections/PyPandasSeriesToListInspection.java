// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.psi.types.PyLiteralStringType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTypedElement;
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
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PyPandasSeriesToListInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspPandasSeriesValuesReplaceWithTolist();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPandas();
    }

    // JB suppressId="PyPackages"
    @Override
    public String getID() {
        return "PyPackages";
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
        public void visitPyCallExpression(PyCallExpression node) {
            if (PsiTreeUtil.hasErrorElements(node)) {
                return;
            }
            PyExpression callee = node.getCallee();
            if (callee == null || !"list".equals(callee.getText()) || node.getArguments().length != 1) {
                return;
            }
            PyExpression argument = node.getArguments()[0];
            if (!(argument instanceof PyQualifiedExpression qualifiedArgument)) {
                return;
            }
            if (!"values".equals(qualifiedArgument.getReferencedName())) {
                return;
            }

            PyExpression qualifier = qualifiedArgument.getQualifier();
            if (qualifier == null) {
                return;
            }

            if (hasSeriesType(qualifier, myTypeEvalContext)) {
                registerProblem(node, PyLocalize.inspPandasSeriesValuesReplaceWithTolist().get(), new DSPandasSeriesToListQuickFix());
            }
        }

        /**
         * Checks whether expression possibly has type Series.
         */
        private static boolean hasSeriesType(PyExpression expression, TypeEvalContext context) {
            if (expression instanceof PySubscriptionExpression subscription) {
                PyType type = PyLiteralType.upcastLiteralToClass(getType(context, subscription.getIndexExpression()));
                return isPyClassWithName(type, "str") || type instanceof PyLiteralStringType;
            }

            PyType expressionType = context.getType(expression);
            if (isPyClassWithName(expressionType, "Series")) {
                return true;
            }

            if (!PyTypeUtilKt.isUnknown(expressionType)) {
                return false;
            }
            return isQualifiedDataframeCall(expression, context);
        }

        private static boolean isQualifiedDataframeCall(PyExpression expression, TypeEvalContext context) {
            if (!(expression instanceof PyQualifiedExpression qualifiedExpression)) {
                return false;
            }
            return isPyClassWithName(getType(context, qualifiedExpression.getQualifier()), "DataFrame");
        }
    }

    // Consulo: JB extends PsiUpdateModCommandQuickFix (ModCommand API missing), ported as a classic LocalQuickFix
    private static class DSPandasSeriesToListQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixPandasSeriesValuesReplaceWithTolist();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyCallExpression functionCall)) {
                return;
            }

            PyExpression[] arguments = functionCall.getArguments();
            if (arguments.length != 1 || !(arguments[0] instanceof PyQualifiedExpression argument)) {
                return;
            }
            PyExpression argumentQualifier = argument.getQualifier();
            if (argumentQualifier == null) {
                return;
            }

            String toListCallString = argumentQualifier.getText() + ".to_list()";

            PyExpressionStatement toListCallStatement = PyElementGenerator.getInstance(project).createFromText(
                LanguageLevel.forElement(functionCall),
                PyExpressionStatement.class,
                toListCallString
            );

            functionCall.replace(toListCallStatement.getExpression());
        }
    }

    private static boolean isPyClassWithName(@Nullable PyType type, String typeName) {
        return type instanceof PyClassType && typeName.equals(type.getName());
    }

    private static @Nullable PyType getType(TypeEvalContext context, @Nullable PyTypedElement element) {
        if (element == null) {
            return null;
        }
        return context.getType(element);
    }
}
