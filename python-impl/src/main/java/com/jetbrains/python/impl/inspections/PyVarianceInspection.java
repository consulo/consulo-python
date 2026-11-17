// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyExpectedVarianceJudgment;
import com.jetbrains.python.impl.psi.types.PyInferredVarianceJudgment;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static com.jetbrains.python.psi.types.PyVariance.BIVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.CONTRAVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.COVARIANT;

@ExtensionImpl
public class PyVarianceInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameVarianceChecker();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
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
        TypeExpressionVisitor typeExpressionVisitor = new TypeExpressionVisitor(holder, context);
        return new PotentialLocationsVisitor(typeExpressionVisitor, holder, context);
    }

    private static class PotentialLocationsVisitor extends PyInspectionVisitor {
        private final TypeExpressionVisitor typeExpressionVisitor;
        private final ProblemsHolder holder;
        private final TypeEvalContext context;

        PotentialLocationsVisitor(TypeExpressionVisitor typeExpressionVisitor, ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
            this.typeExpressionVisitor = typeExpressionVisitor;
            this.holder = holder;
            this.context = context;
        }

        // Inspection: "Obsolete variance for constrained TypeVar"
        @Override
        @RequiredReadAction
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            super.visitPyAssignmentStatement(node);
            if (!(node.getAssignedValue() instanceof PyCallExpression typeVarCall)) {
                return;
            }
            Ref<PyType> typeRef = PyTypingTypeProvider.getType(typeVarCall, context);
            if (typeRef == null || !(typeRef.get() instanceof PyTypeVarType typeVarType)) {
                return;
            }
            List<@Nullable PyType> constraints = typeVarType.getConstraints();
            if (constraints.size() < 2) {
                return;
            }
            if (constraints.size() > 7) {
                return; // escape hatch wrt. performance
            }
            if (typeVarType.getVariance() != COVARIANT && typeVarType.getVariance() != CONTRAVARIANT) {
                return;
            }
            PyArgumentList argList = typeVarCall.getArgumentList();
            if (argList == null) {
                return;
            }
            PyExpression varianceArg = null;
            for (PyExpression arg : argList.getArguments()) {
                if ("covariant".equals(arg.getName()) || "contravariant".equals(arg.getName())) {
                    varianceArg = arg;
                    break;
                }
            }
            if (varianceArg == null) {
                return;
            }
            // now check that at least two constraints have a subtype relation
            for (int idx1 = 0; idx1 < constraints.size(); idx1++) {
                PyType c1 = constraints.get(idx1);
                for (int idx2 = idx1 + 1; idx2 < constraints.size(); idx2++) {
                    PyType c2 = constraints.get(idx2);
                    if (PyTypeChecker.match(c1, c2, context)) {
                        return;
                    }
                    if (PyTypeChecker.match(c2, c1, context)) {
                        return;
                    }
                }
            }
            // no two constraints have a subtype relation
            holder.newProblem(PyLocalize.inspVarianceCheckerSuperfluous())
                .range(varianceArg)
                .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                .create();
        }

        // Below for inspection: "Incompatible variance"

        // Class attribute type annotation
        @Override
        @RequiredReadAction
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);
            if (node.getContainingClass() == null) {
                return;
            }
            if (node.isQualified()) {
                return;
            }
            PyAnnotation annotation = node.getAnnotation();
            if (annotation == null) {
                return;
            }
            annotation.accept(typeExpressionVisitor);
        }

        // Parameter type annotation
        @Override
        @RequiredReadAction
        public void visitPyNamedParameter(PyNamedParameter node) {
            super.visitPyNamedParameter(node);
            PyAnnotation annotation = node.getAnnotation();
            if (annotation == null) {
                return;
            }
            annotation.accept(typeExpressionVisitor);
        }

        // Return type annotation
        @Override
        @RequiredReadAction
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);
            PyAnnotation annotation = node.getAnnotation();
            if (annotation == null) {
                return;
            }
            annotation.accept(typeExpressionVisitor);
        }

        // Superclasses (bases)
        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);
            for (PyExpression superClassExpression : node.getSuperClassExpressions()) {
                superClassExpression.accept(typeExpressionVisitor);
            }
        }
    }

    private static class TypeExpressionVisitor extends PyElementVisitor {
        private final ProblemsHolder holder;
        private final TypeEvalContext context;

        TypeExpressionVisitor(ProblemsHolder holder, TypeEvalContext context) {
            this.holder = holder;
            this.context = context;
        }

        @Override
        public void visitPyAnnotation(PyAnnotation node) {
            node.acceptChildren(this);
        }

        @Override
        public void visitPyExpression(PyExpression node) {
            node.acceptChildren(this);
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
            PyExpression syntheticElement = PyUtil.createExpressionFromFragment(node.getStringValue(), node);
            if (syntheticElement == null) {
                return;
            }
            syntheticElement.acceptChildren(this);
        }

        @Override
        @RequiredReadAction
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            Ref<PyType> typeRef = PyTypingTypeProvider.getType(node, context);
            PyType type = typeRef != null ? typeRef.get() : null;
            if (type == null) {
                return;
            }
            if (!(type instanceof PyTypeVarType)) {
                return;
            }
            onPyTypeVarTypeUsedInAnnotation(holder, node, context);
        }
    }

    @RequiredReadAction
    private static void onPyTypeVarTypeUsedInAnnotation(ProblemsHolder holder, PyReferenceExpression node, TypeEvalContext context) {
        PyTypeParameterType typeParameterType = PyInferredVarianceJudgment.findTypeVariable(node, context);
        if (typeParameterType == null) {
            return;
        }
        if (isIgnoredLocation(node)) {
            return;
        }
        if (!isOpenTypeParameter(node, typeParameterType)) {
            return;
        }

        PyClass containingClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
        boolean isInProtocol = containingClass != null && PyProtocolsKt.isProtocol(containingClass, context);
        PySubscriptionExpression subscriptionExpression = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
        boolean isInProtocolHeader = subscriptionExpression != null &&
            subscriptionExpression.getParent() instanceof PyArgumentList &&
            subscriptionExpression.getParent().getParent() instanceof PyClass;
        if (isInProtocol && isInProtocolHeader) {
            checkProtocol(holder, node, typeParameterType, context);
        }
        else {
            checkClass(holder, node, typeParameterType, context);
        }
    }

    @RequiredReadAction
    private static void checkProtocol(ProblemsHolder holder,
                                      PyReferenceExpression node,
                                      PyTypeParameterType typeParameterType,
                                      TypeEvalContext context) {
        PyVariance varianceExpected = PyInferredVarianceJudgment.getInferredVariance(node, context);
        if (varianceExpected == null) {
            return;
        }
        PyVariance varianceActual = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParameterType, context);

        boolean isInProtocolSubscriptionExpr = isInProtocolSubscriptionExpr(node);
        if (isInProtocolSubscriptionExpr) {
            if (varianceExpected != varianceActual) {
                if (varianceExpected == BIVARIANT && varianceActual == COVARIANT) {
                    // skip since conformance tests expect BIVARIANT to be COVARIANT
                }
                else {
                    PyVariance effectiveVarianceExpected = varianceExpected == BIVARIANT ? COVARIANT : varianceExpected;
                    PsiElement physicalNode = PyUtil.getFragmentContext(node);
                    if (physicalNode == null) {
                        return;
                    }
                    holder.newProblem(PyLocalize.inspVarianceCheckerProtocolsKeepEffectiveVariance(
                            effectiveVarianceExpected.name().toLowerCase(Locale.ROOT), varianceActual.name().toLowerCase(Locale.ROOT)))
                        .range(physicalNode)
                        .create();
                }
            }
        }
        else {
            checkIncompatibleVariance(varianceActual, varianceExpected, holder, node);
        }
    }

    private static boolean isInProtocolSubscriptionExpr(PyReferenceExpression node) {
        PySubscriptionExpression parentSubscriptionExpr;
        if (node.getParent() instanceof PySubscriptionExpression subscriptionExpression) {
            parentSubscriptionExpr = subscriptionExpression;
        }
        else if (node.getParent() instanceof PyTupleExpression tupleExpression &&
            tupleExpression.getParent() instanceof PySubscriptionExpression subscriptionExpression) {
            parentSubscriptionExpr = subscriptionExpression;
        }
        else {
            return false;
        }
        if (node == parentSubscriptionExpr.getIndexExpression()) {
            return true;
        }
        if (!(parentSubscriptionExpr.getIndexExpression() instanceof PyTupleExpression indexTupleExpression)) {
            return false;
        }
        PyTupleExpression nodeTupleParent = PsiTreeUtil.getParentOfType(node, PyTupleExpression.class);
        return indexTupleExpression == nodeTupleParent;
    }

    @RequiredReadAction
    private static void checkClass(ProblemsHolder holder,
                                   PyReferenceExpression node,
                                   PyTypeParameterType typeParameterType,
                                   TypeEvalContext context) {
        PyVariance varianceExpected = PyExpectedVarianceJudgment.getExpectedVariance(node, context).toPyVariance();
        if (varianceExpected == null) {
            return;
        }
        if (varianceExpected == BIVARIANT) {
            return;
        }
        PyVariance varianceInferred = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParameterType, context);

        checkIncompatibleVariance(varianceInferred, varianceExpected, holder, node);
    }

    private static boolean isIgnoredLocation(PyReferenceExpression node) {
        PsiElement parent = PyUtil.getFragmentContextAwareParent(node);
        while (parent != null &&
            !(parent instanceof PyFunction || parent instanceof PyClass || parent instanceof PyTypeDeclarationStatement ||
                parent instanceof PyAssignmentStatement)) {
            parent = PyUtil.getFragmentContextAwareParent(parent);
        }

        if (parent instanceof PyFunction function) {
            if (PyInferredVarianceJudgment.functionDoesNotAffectVarianceInference(function)) {
                return true;
            }
        }
        else if (parent instanceof PyTypeDeclarationStatement typeDeclarationStatement) {
            PyExpression tgtExpr = typeDeclarationStatement.getTarget();
            if (tgtExpr instanceof PyTargetExpression targetExpression &&
                PyInferredVarianceJudgment.attributeDoesNotAffectVarianceInference(targetExpression)) {
                return true;
            }
        }
        else if (parent instanceof PyAssignmentStatement assignmentStatement) {
            for (PyExpression tgtExpr : assignmentStatement.getTargets()) {
                if (tgtExpr instanceof PyTargetExpression targetExpression &&
                    PyInferredVarianceJudgment.attributeDoesNotAffectVarianceInference(targetExpression)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOpenTypeParameter(PyReferenceExpression node, PyTypeParameterType typeParameterType) {
        PsiElement scopeOwner = typeParameterType.getScopeOwner();
        if (!(scopeOwner instanceof PyClass)) {
            return false; // type parameter is invariant
        }
        int containingClassesCount = 0;
        int containingFunctionsCount = 0;
        for (PsiElement it = node; it != null && it != scopeOwner; it = PyUtil.getFragmentContextAwareParent(it)) {
            if (it instanceof PyClass) {
                containingClassesCount++;
            }
            if (it instanceof PyFunction) {
                containingFunctionsCount++;
            }
        }
        if (containingClassesCount > 0 || containingFunctionsCount > 1) {
            // type parameter is captured already
            return false;
        }
        return true;
    }

    @RequiredReadAction
    private static void checkIncompatibleVariance(PyVariance varianceInferred,
                                                  PyVariance varianceExpected,
                                                  ProblemsHolder holder,
                                                  PyReferenceExpression node) {
        if (varianceExpected.isCompatibleWithActual(varianceInferred)) {
            return;
        }

        PsiElement physicalNode = PyUtil.getFragmentContext(node);
        if (physicalNode == null) {
            return;
        }
        holder.newProblem(PyLocalize.inspVarianceCheckerIncompatible(
                varianceExpected.name().toLowerCase(Locale.ROOT), varianceInferred.name().toLowerCase(Locale.ROOT)))
            .range(physicalNode)
            .create();
    }
}
