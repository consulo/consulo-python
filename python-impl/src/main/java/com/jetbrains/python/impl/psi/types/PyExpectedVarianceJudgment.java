// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.types.PyBaseVariance;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyExpectedVariance;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.types.PyVariance.BIVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.CONTRAVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.COVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.INVARIANT;

public final class PyExpectedVarianceJudgment {
    private PyExpectedVarianceJudgment() {
    }

    /**
     * Return the expected variance for the given location. The location must be a reference inside a type expression.
     * However, note that some locations need to be ignored when computing the inferred variance or when checking/inspecting the correct
     * use of type parameters w.r.t. their variance.
     * <p>
     * Returns {@link PyExpectedVariance.NONE} iff the given location is not applicable for variance judgment.
     *
     * @see PyExpectedVariance.NONE
     */
    public static PyExpectedVariance getExpectedVariance(PyReferenceExpression element, TypeEvalContext context) {
        return getExpectedVariance((PsiElement) element, context);
    }

    /**
     * Return the expected variance for the given location.
     */
    private static PyExpectedVariance getExpectedVariance(PsiElement element, TypeEvalContext context) {
        PsiElement parent = PyUtil.getFragmentContextAwareParent(element);
        if (parent == null) {
            return PyExpectedVariance.NONE.INSTANCE;
        }

        if (element instanceof PyClass ||
            element instanceof PyTypeAliasStatement ||
            element instanceof PyExpressionStatement) { // parent of synthetic expressions created by PyElementGenerator#createExpressionFromText()
            return BIVARIANT;
        }
        else if (element instanceof PyAssignmentStatement assignmentStatement) {
            return fromAssignmentStatement(assignmentStatement, context);
        }
        else if (element instanceof PyFunction) {
            return COVARIANT;
        }
        else if (element instanceof PyTypeDeclarationStatement typeDeclarationStatement) {
            return fromTypeDeclarationStatement(typeDeclarationStatement, parent, context);
        }
        else if (element instanceof PyNamedParameter) {
            return getExpectedVariance(parent, context).not();
        }
        // keep the following list as precise and short as possible to enforce returning NONE whenever possible
        else if (element instanceof PyListLiteralExpression ||
                 element instanceof PyArgumentList ||
                 element instanceof PyBinaryExpression ||
                 element instanceof PyParameterList ||
                 element instanceof PyStatementList ||
                 element instanceof PyAnnotation ||
                 element instanceof PyStringLiteralExpression ||
                 element instanceof PyReferenceExpression ||
                 element instanceof PySubscriptionExpression ||
                 element instanceof PyTupleExpression ||
                 element instanceof PyStarExpression) {
            PsiElement grandParent = PyUtil.getFragmentContextAwareParent(parent);
            if (parent instanceof PySubscriptionExpression subscriptionExpression) {
                return fromElementInSubscriptionExpression(0, subscriptionExpression, context);
            }
            else if (parent instanceof PyTupleExpression tupleExpression && grandParent instanceof PySubscriptionExpression grandSubscription) {
                return fromElementInSubscriptionExpression(ArrayUtil.indexOf(tupleExpression.getElements(), element), grandSubscription, context);
            }
            else {
                return getExpectedVariance(parent, context);
            }
        }
        else {
            return PyExpectedVariance.NONE.INSTANCE;
        }
    }

    private static PyExpectedVariance fromTypeDeclarationStatement(PyTypeDeclarationStatement element,
                                                                   PsiElement parent,
                                                                   TypeEvalContext context) {
        if (!(element.getTarget() instanceof PyTargetExpression targetExpr)) {
            return PyExpectedVariance.NONE.INSTANCE;
        }
        return fromAssignmentOrTypeDeclarationStatement(targetExpr, element, context);
    }

    private static PyExpectedVariance fromAssignmentStatement(PyAssignmentStatement element, TypeEvalContext context) {
        PyExpression[] targets = element.getTargets();
        if (targets.length != 1 || !(targets[0] instanceof PyTargetExpression targetExpr)) {
            return PyExpectedVariance.NONE.INSTANCE;
        }
        return fromAssignmentOrTypeDeclarationStatement(targetExpr, element, context);
    }

    private static PyExpectedVariance fromAssignmentOrTypeDeclarationStatement(PyTargetExpression targetExpr,
                                                                               PsiElement parent,
                                                                               TypeEvalContext context) {
        PsiElement parentClass = PyUtil.getFragmentContextAwareParent(parent);
        while (parentClass != null && !(parentClass instanceof PyFunction || parentClass instanceof PyClass)) {
            parentClass = PyUtil.getFragmentContextAwareParent(parentClass);
        }
        if (parentClass instanceof PyFunction) {
            if (isDunderInit(parentClass)) {
                if (!PyUtil.isInstanceAttribute(targetExpr)) {
                    return PyExpectedVariance.NONE.INSTANCE;
                }
                if (PyTypeUtil.isReadOnlyAttribute(targetExpr, context)) {
                    return COVARIANT;
                }
                return INVARIANT;
            }
            return PyExpectedVariance.NONE.INSTANCE;
        }
        else if (parentClass instanceof PyClass) {
            if (PyTypeUtil.isReadOnlyAttribute(targetExpr, context)) {
                return COVARIANT;
            }
            return INVARIANT;
        }
        return PyExpectedVariance.NONE.INSTANCE;
    }

    private static boolean isDunderInit(@Nullable PsiElement parentClass) {
        return parentClass instanceof PyFunction function && PyNames.INIT.equals(function.getName());
    }

    private static PyExpectedVariance fromElementInSubscriptionExpression(int refIndex,
                                                                          PySubscriptionExpression subscriptionExpr,
                                                                          TypeEvalContext context) {
        if (!(subscriptionExpr.getOperand() instanceof PyReferenceExpression qualifier)) {
            return PyExpectedVariance.NONE.INSTANCE;
        }
        PsiElement physicalElement = PyUtil.getFragmentContext(qualifier);
        PyNamedParameter parentNamedParameter = PsiTreeUtil.getStubOrPsiParentOfType(physicalElement, PyNamedParameter.class);
        if (parentNamedParameter != null && parentNamedParameter.isSelf()) {
            return PyExpectedVariance.NONE.INSTANCE;
        }

        Collection<String> qualifierQNames = PyTypingTypeProvider.resolveToQualifiedNames(qualifier, context);
        if (ContainerUtil.exists(qualifierQNames,
                                 it -> Set.of(PyTypingTypeProvider.GENERIC, PyTypingTypeProvider.PROTOCOL, PyTypingTypeProvider.PROTOCOL_EXT)
                                     .contains(it))) {
            return BIVARIANT; // for T in: `class C(Generic[T])` or `class C(Protocol[T])`
        }
        if (ContainerUtil.exists(qualifierQNames,
                                 it -> Set.of(PyTypingTypeProvider.CALLABLE, PyTypingTypeProvider.CALLABLE_EXT).contains(it)) && refIndex == 0) {
            PyExpectedVariance outerVariance = getExpectedVariance(subscriptionExpr, context);
            return outerVariance.times(CONTRAVARIANT);
        }

        Ref<PyType> qualifierTypeRef = PyTypingTypeProvider.getType(subscriptionExpr.getOperand(), context);
        PyType qualifierType = qualifierTypeRef != null ? qualifierTypeRef.get() : null;
        if (qualifierType instanceof PyClassType classType && !classType.isParameterized()) {
            // convert raw types to generic types
            PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), context);
            qualifierType = genericDefinitionType != null ? genericDefinitionType : qualifierType;
        }
        if (qualifierType instanceof PyClassType classType && classType.isParameterized()) {
            PyBaseVariance paramVariance = getTypeParameterVarianceAtIndex(classType, refIndex, context);
            PyExpectedVariance outerVariance = getExpectedVariance(subscriptionExpr, context);
            if (outerVariance == PyExpectedVariance.NONE.INSTANCE) {
                return paramVariance.times(BIVARIANT);
            }
            return outerVariance.times(paramVariance);
        }
        return getExpectedVariance(subscriptionExpr, context);
    }

    private static PyBaseVariance getTypeParameterVarianceAtIndex(PyClassType qualifierType, int index, TypeEvalContext context) {
        if (qualifierType.isParameterized()) {
            if (PyNames.FQN.TUPLE.equals(qualifierType.getClassQName())) {
                return COVARIANT;
            }
            // check definition type since generic type aliases are parameterized, i.e.: `A_Alias_1 = ClassA[T_co]` will be ClassA[Any]
            PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(qualifierType.getPyClass(), context);
            PyClassType definitionType = genericDefinitionType != null ? genericDefinitionType : qualifierType;
            PyTypeParameterType typeParamType = typeParameterAt(definitionType.getTypeArguments(), index);
            if (typeParamType == null) {
                typeParamType = typeParameterAt(qualifierType.getTypeArguments(), index);
            }
            if (typeParamType == null) {
                return PyExpectedVariance.NONE.INSTANCE;
            }
            return PyInferredVarianceJudgment.getIntermediateVariance(typeParamType, context);
        }
        return PyExpectedVariance.NONE.INSTANCE;
    }

    private static @Nullable PyTypeParameterType typeParameterAt(List<? extends @Nullable PyType> typeArguments, int index) {
        return index >= 0 && index < typeArguments.size() && typeArguments.get(index) instanceof PyTypeParameterType typeParameterType
            ? typeParameterType
            : null;
    }
}
