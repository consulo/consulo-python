// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssertStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
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

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

@ExtensionImpl
public class PyPandasTruthValueIsAmbiguousInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspPandasTruthValueIsAmbiguousDf();
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
        private final ProblemsHolder myHolder;

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
            myHolder = holder;
        }

        @Override
        @RequiredReadAction
        public void visitPyAssertStatement(PyAssertStatement node) {
            super.visitPyAssertStatement(node);
            PyExpression[] arguments = node.getArguments();
            if (arguments.length == 0) {
                return;
            }
            reportProblems(arguments[0]);
        }

        @Override
        @RequiredReadAction
        public void visitPyConditionalStatementPart(PyConditionalStatementPart node) {
            super.visitPyConditionalStatementPart(node);
            PyExpression condition = node.getCondition();
            if (condition == null) {
                return;
            }
            reportProblems(condition);
        }

        @RequiredReadAction
        private void reportProblems(PyExpression expression) {
            if (expression instanceof PyBinaryExpression) {
                expression.accept(new BinaryExpressionRecursiveVisitor(myHolder, myTypeEvalContext));
            }
            else {
                reportProblemIfPandasAmbiguousTarget(expression, myHolder, myTypeEvalContext, true);
            }
        }
    }

    private static class BinaryExpressionRecursiveVisitor extends PyRecursiveElementVisitor {
        private final ProblemsHolder myHolder;
        private final TypeEvalContext myContext;

        BinaryExpressionRecursiveVisitor(ProblemsHolder holder, TypeEvalContext context) {
            myHolder = holder;
            myContext = context;
        }

        @Override
        @RequiredReadAction
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            boolean isIdentity = node.isOperator(PyNames.IS) || node.isOperator("isnot");
            if (!isIdentity) {
                boolean suggestedQuickFixes = node.isOperator(PyNames.AND) || node.isOperator(PyNames.OR) || node.isOperator("==");
                reportProblemIfPandasAmbiguousTarget(node.getLeftExpression(), myHolder, myContext, suggestedQuickFixes);
                reportProblemIfPandasAmbiguousTarget(node.getRightExpression(), myHolder, myContext, suggestedQuickFixes);
            }
            super.visitPyBinaryExpression(node);
        }
    }

    @RequiredReadAction
    private static void reportProblemIfPandasAmbiguousTarget(@Nullable PyExpression expression,
                                                             ProblemsHolder holder,
                                                             TypeEvalContext typeEvalContext,
                                                             boolean suggestQuickFixes) {
        if (expression == null) {
            return;
        }
        PandasAmbiguousTarget pandasAmbiguousTarget;
        if (expression instanceof PyPrefixExpression prefixExpression) {
            pandasAmbiguousTarget = resolvePandasAmbiguousTarget(prefixExpression.getOperand(), typeEvalContext);
        }
        else if (expression instanceof PyReferenceExpression) {
            pandasAmbiguousTarget = resolvePandasAmbiguousTarget(expression, typeEvalContext);
        }
        else {
            pandasAmbiguousTarget = null;
        }
        if (pandasAmbiguousTarget == null) {
            return;
        }
        LocalQuickFix[] quickFixes;
        if (!suggestQuickFixes) {
            quickFixes = LocalQuickFix.EMPTY_ARRAY;
        }
        else if (expression instanceof PyPrefixExpression) {
            quickFixes = new LocalQuickFix[]{new NotPrefixExpressionReplaceWithEmptyCheckQuickFix()};
        }
        else if (expression instanceof PyReferenceExpression) {
            quickFixes = new LocalQuickFix[]{
                new ReferenceExpressionReplaceWithEmptyCheckQuickFix(),
                new ReferenceExpressionReplaceWithExplicitNotNoneCheckQuickFix()
            };
        }
        else {
            quickFixes = LocalQuickFix.EMPTY_ARRAY;
        }
        holder.newProblem(pandasAmbiguousTarget.myInspectionMessage.get())
            .range(expression)
            .withFixes(quickFixes)
            .create();
    }

    private enum PandasAmbiguousTarget {
        DATA_FRAME("pandas.core.frame.DataFrame", PyLocalize::inspPandasTruthValueIsAmbiguousDf),
        SERIES("pandas.core.series.Series", PyLocalize::inspPandasTruthValueIsAmbiguousSeries);

        private final String myQualifiedName;
        // JB: inspectionBundleName (PyPsiBundle key)
        private final Supplier<LocalizeValue> myInspectionMessage;

        PandasAmbiguousTarget(String qualifiedName, Supplier<LocalizeValue> inspectionMessage) {
            myQualifiedName = qualifiedName;
            myInspectionMessage = inspectionMessage;
        }
    }

    private static @Nullable PandasAmbiguousTarget resolvePandasAmbiguousTarget(@Nullable PyExpression expression,
                                                                               TypeEvalContext typeEvalContext) {
        if (expression == null) {
            return null;
        }
        PyType type = typeEvalContext.getType(expression);
        Collection<? extends @Nullable PyType> typesToCheck = type instanceof PyUnionType unionType
            ? unionType.getMembers()
            : Collections.singletonList(type);
        for (PyType memberType : typesToCheck) {
            PyQualifiedNameOwner declarationElement = memberType != null ? memberType.getDeclarationElement() : null;
            String qualifiedName = declarationElement != null ? declarationElement.getQualifiedName() : null;
            for (PandasAmbiguousTarget target : PandasAmbiguousTarget.values()) {
                if (Objects.equals(target.myQualifiedName, qualifiedName)) {
                    return target;
                }
            }
        }
        return null;
    }

    private static class ReferenceExpressionReplaceWithEmptyCheckQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixPandasTruthValueIsAmbiguousEmptinessCheck();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyReferenceExpression element)) {
                return;
            }
            PyElementGenerator factory = PyElementGenerator.getInstance(project);
            PyExpression newElement = factory.createExpressionFromText(LanguageLevel.forElement(element), "not " + element.getText() + ".empty");
            element.replace(newElement);
        }
    }

    private static class ReferenceExpressionReplaceWithExplicitNotNoneCheckQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixPandasTruthValueIsAmbiguousNoneCheck();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyReferenceExpression element)) {
                return;
            }
            PyElementGenerator factory = PyElementGenerator.getInstance(project);
            PyExpression newElement = factory.createExpressionFromText(LanguageLevel.forElement(element), element.getText() + " is not None");
            element.replace(newElement);
        }
    }

    private static class NotPrefixExpressionReplaceWithEmptyCheckQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixPandasTruthValueIsAmbiguousEmptinessCheck();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyPrefixExpression element)) {
                return;
            }
            PyExpression operand = element.getOperand();
            if (operand == null) {
                return;
            }
            PyElementGenerator factory = PyElementGenerator.getInstance(project);
            PyExpression newElement = factory.createExpressionFromText(LanguageLevel.forElement(element), operand.getText() + ".empty");
            element.replace(newElement);
        }
    }
}
