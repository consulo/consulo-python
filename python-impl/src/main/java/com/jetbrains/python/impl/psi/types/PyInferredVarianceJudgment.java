// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.PyVarianceIntermediateResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StackOverflowPreventedException;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.types.PyVariance.BIVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.CONTRAVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.COVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.INFER_VARIANCE;
import static com.jetbrains.python.psi.types.PyVariance.INVARIANT;

public final class PyInferredVarianceJudgment {
    private static final RecursionGuard<TypeVariableId> recursionGuard = RecursionManager.createGuard("PyInferredVarianceJudgment");

    private PyInferredVarianceJudgment() {
    }

    /**
     * Returns true for references to non-invariant type parameter at locations where variance is ignored
     * and is supposed to be treated as invariant, e.g., for type parameters of static methods in classes.
     */
    public static boolean isEffectivelyInvariant(@Nullable PyTypedElement element, TypeEvalContext context) {
        if (!(element instanceof PyReferenceExpression reference)) {
            return false;
        }
        PyVariance inferredVariance = getDeclaredOrInferredVariance(reference, context);
        if (inferredVariance == null) {
            return false;
        }
        if (inferredVariance == INVARIANT) {
            return false;
        }
        PsiElement nearest = reference;
        while (nearest != null && !(nearest instanceof PyFunction || nearest instanceof PyClass)) {
            nearest = PyUtil.getFragmentContextAwareParent(nearest);
        }
        if (!(nearest instanceof PyFunction nearestFunction)) {
            return false;
        }
        return functionIgnoresVariance(nearestFunction);
    }

    /**
     * Convenience method for {@link #getDeclaredOrInferredVariance(PyTypeParameterType, TypeEvalContext)}:
     * It calls {@link #findTypeVariable} first and passes the resulting type parameter type to {@link #getDeclaredOrInferredVariance}.
     */
    public static @Nullable PyVariance getDeclaredOrInferredVariance(@Nullable PyTypedElement element, TypeEvalContext context) {
        PyTypeParameterType typeParameterType = findTypeVariable(element, context);
        if (typeParameterType == null) {
            return null;
        }
        return guardedGetDeclaredOrInferredVarianceOrInvariant(typeParameterType, VarianceInferenceKind.DECLARED_AND_INFERRED, context);
    }

    /**
     * Convenience method for {@link #getInferredVariance(PyTypeParameterType, TypeEvalContext)}:
     * It calls {@link #findTypeVariable} first and passes the resulting type parameter type to {@link #getInferredVariance}.
     */
    public static @Nullable PyVariance getInferredVariance(@Nullable PyTypedElement element, TypeEvalContext context) {
        PyTypeParameterType typeVarType = findTypeVariable(element, context);
        if (typeVarType == null) {
            return null;
        }
        return guardedGetDeclaredOrInferredVarianceOrInvariant(typeVarType, VarianceInferenceKind.INFERRED_ONLY, context);
    }

    /**
     * Returns the type parameter type if the given element is a type parameter or a reference to a type parameter
     */
    public static @Nullable PyTypeParameterType findTypeVariable(@Nullable PyTypedElement element, TypeEvalContext context) {
        PyType typeParamType;
        if (element instanceof PyTypeParameter typeParameter) {
            typeParamType = PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(typeParameter, context);
        }
        else if (element instanceof PyReferenceExpression || element instanceof PyCallExpression) {
            Ref<PyType> typeRef = PyTypingTypeProvider.getType((PyExpression) element, context);
            typeParamType = typeRef != null ? typeRef.get() : null;
        }
        else {
            return null;
        }
        return typeParamType instanceof PyTypeParameterType typeParameterType ? typeParameterType : null;
    }

    /**
     * Returns either the declared variance if it is declared explicitly, or infers the variance of the given type parameter from its usages.
     */
    public static PyVariance getDeclaredOrInferredVariance(PyTypeParameterType typeParameterType, TypeEvalContext context) {
        return guardedGetDeclaredOrInferredVarianceOrInvariant(typeParameterType, VarianceInferenceKind.DECLARED_AND_INFERRED, context);
    }

    /**
     * Returns the inferred the variance of the given type parameter from its usages
     */
    public static PyVariance getInferredVariance(PyTypeParameterType typeParameterType, TypeEvalContext context) {
        return guardedGetDeclaredOrInferredVarianceOrInvariant(typeParameterType, VarianceInferenceKind.INFERRED_ONLY, context);
    }

    /**
     * Returns either the declared variance if it is declared explicitly, or infers the variance of the given type parameter from its usages.
     * Returns {@link PyVarianceIntermediateResult.RECURSIVE} iff the given parameter type's variance depends on itself.
     *
     * @see PyVarianceIntermediateResult.RECURSIVE
     */
    public static PyVarianceIntermediateResult getIntermediateVariance(PyTypeParameterType typeParameterType, TypeEvalContext context) {
        return guardedGetDeclaredOrInferredVariance(typeParameterType, VarianceInferenceKind.DECLARED_AND_INFERRED, context);
    }

    /**
     * Returns the variance of the given type parameter from its usages in the return-type of a function.
     * <p>
     * Respects effective scope owner of function:
     * <pre>{@code
     * def f[F]() -> Callable[[F], F]: ...
     * }</pre>
     * <p>
     * Usually, the scope owner would be function {@code f}. However, since {@code F} is not used/bound in the arguments of {@code f} nor via its default
     * type (it has none), {@code F} is still open. Since {@code F} is used only in the returned callable type of {@code f}, its effective scope owner is
     * that returned function.
     *
     * @see UsageCollector#collectInFunctionReturn
     */
    public static PyVarianceIntermediateResult getFunctionReturnVariance(PyTypeParameterType typeParameterType, TypeEvalContext context) {
        return guardedGetDeclaredOrInferredVariance(typeParameterType, VarianceInferenceKind.FUNCTION_RETURN_ONLY, context);
    }

    private enum VarianceInferenceKind {
        DECLARED_AND_INFERRED,
        INFERRED_ONLY,
        FUNCTION_RETURN_ONLY,
    }

    private static PyVariance guardedGetDeclaredOrInferredVarianceOrInvariant(PyTypeParameterType typeParameterType,
                                                                              VarianceInferenceKind inferenceKind,
                                                                              TypeEvalContext context) {
        PyVarianceIntermediateResult result = guardedGetDeclaredOrInferredVariance(typeParameterType, inferenceKind, context);
        if (result instanceof PyVarianceIntermediateResult.RECURSIVE) {
            return INVARIANT;
        }
        return (PyVariance) result;
    }

    private static PyVarianceIntermediateResult guardedGetDeclaredOrInferredVariance(PyTypeParameterType typeParameterType,
                                                                                     VarianceInferenceKind inferenceKind,
                                                                                     TypeEvalContext context) {
        PyQualifiedNameOwner scopeOwner = typeParameterType.getScopeOwner();
        if (inferenceKind != VarianceInferenceKind.FUNCTION_RETURN_ONLY && scopeOwner instanceof PyFunction) {
            return INVARIANT; // inferred/declared variance assumes invariance
        }
        if (inferenceKind == VarianceInferenceKind.FUNCTION_RETURN_ONLY && !(scopeOwner instanceof PyFunction)) {
            return INVARIANT; // inference kind works only with functions
        }
        if (inferenceKind == VarianceInferenceKind.DECLARED_AND_INFERRED && typeParameterType.getVariance() != INFER_VARIANCE) {
            return typeParameterType.getVariance();
        }
        if (scopeOwner == null) {
            return INVARIANT;
        }

        PyVariance cachedVariance = context.getVarianceCache().get(typeParameterType);
        if (cachedVariance != null) {
            return cachedVariance;
        }

        // Notes on recursive situations: Returning BIVARIANT is most permitting and avoids incorrect results.
        // Also, returning BIVARIANT guarantees stable variance inferences independent of the entry point.
        try {
            TypeVariableId tvId = new TypeVariableId(typeParameterType.getName(), scopeOwner);
            PyVarianceIntermediateResult result = recursionGuard.doPreventingRecursion(tvId, true, () -> {
                PyVariance actualResult = doGetInferredVariance(tvId, inferenceKind, context);
                context.getVarianceCache().put(typeParameterType, actualResult);
                return actualResult;
            });

            return result != null ? result : PyVarianceIntermediateResult.RECURSIVE.INSTANCE;
        }
        catch (StackOverflowPreventedException e) {
            // this is supposed to happen in recursive situations and a safe exit. No need to bother tests.
            return PyVarianceIntermediateResult.RECURSIVE.INSTANCE;
        }
    }

    private static PyVariance doGetInferredVariance(TypeVariableId tvId, VarianceInferenceKind inferenceKind, TypeEvalContext context) {
        UsageCollector collector = new UsageCollector(tvId, context);
        if (tvId.scopeOwner() instanceof PyClass pyClass) {
            collector.collectInClass(pyClass);
        }
        else if (tvId.scopeOwner() instanceof PyTypeAliasStatement typeAliasStatement) {
            PyExpression typeExpression = typeAliasStatement.getTypeExpression();
            if (typeExpression == null) {
                return INVARIANT;
            }
            collector.collectReferencesInTypeExpr(typeExpression);
        }
        else if (tvId.scopeOwner() instanceof PyFunction function && inferenceKind == VarianceInferenceKind.FUNCTION_RETURN_ONLY) {
            collector.collectInFunctionReturn(function);
        }
        else {
            return INVARIANT;
        }

        Set<PyVariance> usages = collector.usages;
        if (usages.isEmpty()) {
            // By definition of PEP-695/#variance-inference, variance must be invariant here.
            // However, returning bivariant here improves the soundness of variance inference.
            return BIVARIANT;
        }
        else if (usages.contains(INVARIANT)) {
            return INVARIANT;
        }
        else if (usages.contains(COVARIANT) && usages.contains(CONTRAVARIANT)) {
            return INVARIANT;
        }
        else if (usages.contains(COVARIANT)) {
            return COVARIANT;
        }
        else if (usages.contains(CONTRAVARIANT)) {
            return CONTRAVARIANT;
        }
        else if (usages.contains(BIVARIANT)) {
            return BIVARIANT;
        }
        else {
            return INVARIANT;
        }
    }

    private static final class UsageCollector {
        final TypeVariableId tvId;
        final TypeEvalContext context;
        final Set<PyVariance> usages = new HashSet<>();

        UsageCollector(TypeVariableId tvId, TypeEvalContext context) {
            this.tvId = tvId;
            this.context = context;
        }

        void collectInClass(PyClass clazz) {
            PyType classType = context.getType(clazz);
            if (classType instanceof PyClassLikeType classLikeType) {
                classLikeType.visitMembers(
                    element -> {
                        if (element instanceof PyTargetExpression targetExpression) {
                            collectInAttribute(targetExpression);
                        }
                        else if (element instanceof PyFunction function) {
                            collectInFunction(function);
                        }
                        boolean isInvariantAlready =
                            usages.contains(INVARIANT) || (usages.contains(COVARIANT) && usages.contains(CONTRAVARIANT));
                        return !isInvariantAlready; // performance tweak: no need to search for further usages
                    }, false, context);
            }
            collectInBaseClasses(clazz);
        }

        void collectInFunctionReturn(PyFunction function) {
            if (effectiveScopeOwnerIsReturnedCallable(function)) {
                // TODO: Aliases and quoted type annotations
                PySubscriptionExpression returnedCallableExpression =
                    PyTypingTypeProvider.getAnnotationValue(function, context) instanceof PySubscriptionExpression subscription ? subscription : null;
                PyTupleExpression callableTupleExpression =
                    returnedCallableExpression != null && returnedCallableExpression.getIndexExpression() instanceof PyTupleExpression tuple
                        ? tuple
                        : null;
                PyExpression[] callableElements = callableTupleExpression != null ? callableTupleExpression.getElements() : null;
                PyExpression callableReturnExpression = callableElements != null && callableElements.length > 1 ? callableElements[1] : null;
                if (callableReturnExpression != null) {
                    collectReferencesInTypeExpr(callableReturnExpression);
                }
            }
            else {
                collectReferencesInAnnotation(function);
            }
            if (usages.isEmpty()) {
                // happens when the return type is not annotated
                usages.add(INVARIANT);
            }
        }

        /**
         * This clears the {@link #usages} field when done.
         */
        private boolean effectiveScopeOwnerIsReturnedCallable(PyFunction function) {
            PyType retType = function.getReturnType(context);
            if (!(retType instanceof PyCallableType)) {
                return false;
            }
            collectInFunctionArguments(function); // fills usages if type parameter is used in arguments
            boolean notUsedInArguments = usages.isEmpty();
            usages.clear();
            return notUsedInArguments;
        }

        private void collectInBaseClasses(PyClass clazz) {
            for (PyExpression superClassExpr : PyTypingTypeProvider.getSuperClassExpressions(clazz)) {
                collectReferencesInTypeExpr(superClassExpr);
            }
        }

        private void collectInAttribute(PyTargetExpression target) {
            if (attributeDoesNotAffectVarianceInference(target)) {
                return;
            }
            collectReferencesInAnnotation(target);
        }

        private void collectInFunction(PyFunction function) {
            collectAnnotatedInstanceAttributesInInit(function);
            if (functionDoesNotAffectVarianceInference(function)) {
                return;
            }
            collectReferencesInAnnotation(function);
            collectInFunctionArguments(function);
        }

        private void collectInFunctionArguments(PyFunction function) {
            for (PyParameter parameter : function.getParameterList().getParameters()) {
                if (parameter.isSelf()) {
                    continue;
                }
                if (!(parameter instanceof PyNamedParameter namedParameter)) {
                    continue;
                }
                collectReferencesInAnnotation(namedParameter);
            }
        }

        private void collectAnnotatedInstanceAttributesInInit(PyFunction function) {
            if (!PyNames.INIT.equals(function.getName())) {
                return;
            }
            List<PyTargetExpression> tgtExprs;
            if (function.getStub() == null) {
                List<PyExpression> allTgtExprs = new ArrayList<>();
                for (PyTypeDeclarationStatement declaration : PsiTreeUtil.getChildrenOfTypeAsList(function.getStatementList(),
                                                                                                PyTypeDeclarationStatement.class)) {
                    allTgtExprs.add(declaration.getTarget());
                }
                for (PyAssignmentStatement assignment : PsiTreeUtil.getChildrenOfTypeAsList(function.getStatementList(),
                                                                                           PyAssignmentStatement.class)) {
                    Collections.addAll(allTgtExprs, assignment.getTargets());
                }
                tgtExprs = new ArrayList<>();
                for (PyExpression expression : allTgtExprs) {
                    if (expression instanceof PyTargetExpression targetExpression) {
                        tgtExprs.add(targetExpression);
                    }
                }
            }
            else {
                // use stub if available to avoid un-stubbing
                tgtExprs = PsiTreeUtil.getStubChildrenOfTypeAsList(function, PyTargetExpression.class);
            }

            for (PyTargetExpression tgtExpr : tgtExprs) {
                if (attributeDoesNotAffectVarianceInference(tgtExpr)) {
                    continue;
                }
                if (!PyUtil.isInstanceAttribute(tgtExpr)) {
                    continue;
                }
                if (tgtExpr.getAnnotationValue() == null) {
                    // attributes without type annotations are supposed to be of type Any:
                    // def __init__(self, x: T) -> None:
                    //   self.attr = x # attr is Any
                }
                else {
                    // case for: def __init__(self, x: T) -> None: self.attr: T = x
                    collectReferencesInAnnotation(tgtExpr);
                }
            }
        }

        void collectReferencesInAnnotation(PyAnnotationOwner annotationOwner) {
            PyExpression annotationValue = PyTypingTypeProvider.getAnnotationValue(annotationOwner, context);
            if (annotationValue != null) {
                collectReferencesInTypeExpr(annotationValue);
            }
        }

        void collectReferencesInTypeExpr(PyExpression element) {
            if (element instanceof PyReferenceExpression referenceExpression) {
                Ref<PyType> refType = PyTypingTypeProvider.getType(referenceExpression, context);
                PyTypeParameterType typeParamType =
                    refType != null && refType.get() instanceof PyTypeParameterType typeParameterType ? typeParameterType : null;
                if (typeParamType == null) {
                    PyExpression qualifier = referenceExpression.getQualifier();
                    if (qualifier != null) {
                        collectReferencesInTypeExpr(qualifier);
                    }
                    return;
                }
                if (typeParamType.getName().equals(tvId.name()) && tvId.scopeOwner().equals(typeParamType.getScopeOwner())) {
                    PyVariance exprVariance = PyExpectedVarianceJudgment.getExpectedVariance(referenceExpression, context).toPyVariance();
                    if (exprVariance == null) {
                        return;
                    }
                    usages.add(exprVariance);
                }
            }
            else if (element instanceof PyStringLiteralExpression stringLiteralExpression) {
                PyExpression syntheticElement = PyUtil.createExpressionFromFragment(stringLiteralExpression.getStringValue(), element);
                if (syntheticElement == null) {
                    return;
                }
                collectReferencesInTypeExpr(syntheticElement);
            }
            else {
                @SuppressWarnings("unchecked")
                Collection<PyExpression> refExpressions =
                    PsiTreeUtil.findChildrenOfAnyType(element, PyReferenceExpression.class, PyStringLiteralExpression.class);
                for (PyExpression refExpression : refExpressions) {
                    collectReferencesInTypeExpr(refExpression);
                }
            }
        }
    }

    private record TypeVariableId(String name, PyQualifiedNameOwner scopeOwner) {
    }

    /**
     * Returns true iff
     * - The attribute's name starts with an underscore (i.e., is private or protected).
     */
    public static boolean attributeDoesNotAffectVarianceInference(PyTargetExpression target) {
        return target.getProtectionLevel() != ProtectionLevel.PUBLIC;
    }

    /**
     * Returns true iff either:
     * - The function's name starts with an underscore (i.e., is private or protected), or
     * - If {@link #functionIgnoresVariance} is true
     */
    public static boolean functionDoesNotAffectVarianceInference(PyFunction callable) {
        if (callable.getProtectionLevel() != ProtectionLevel.PUBLIC) {
            return true;
        }
        return functionIgnoresVariance(callable);
    }

    /**
     * Returns true iff the function either:
     * - Has no parameters, or
     * - Is {@code __init__} or {@code __new__}, or
     * - Is decorated as a class or static method.
     */
    public static boolean functionIgnoresVariance(PyFunction callable) {
        if (callable.getParameterList().getParameters().length == 0) {
            return true; // methods with no parameters are unbound instance methods and cannot be called on an instance
        }
        if (PyUtil.isInitOrNewMethod(callable)) {
            return true; // new and init methods take covariant parameters
        }
        PyFunction.Modifier modifier = callable.getModifier();
        if (modifier == PyFunction.Modifier.CLASSMETHOD || modifier == PyFunction.Modifier.STATICMETHOD) {
            return true; // static and class methods ignore variance
        }
        return false;
    }
}
