// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

public final class PyTypeInferenceCspFactory {
    private static final RecursionGuard<PyExpression> recursionGuard = RecursionManager.createGuard("PyTypeInferenceCsp");

    private PyTypeInferenceCspFactory() {
    }

    public static PyTypeChecker.GenericSubstitutions unifySequenceExpression(PySequenceExpression expression, TypeEvalContext context) {
        PyTypeChecker.GenericSubstitutions solution = enterCsp(new SubstitutionsIdentifier(expression), context);
        return solution != null ? solution : PyCollectionTypeUtil.getSequenceSubstitutionsFallback(expression, context);
    }

    public static PyTypeChecker.GenericSubstitutions unifyReceiver(PyCallExpression.PyArgumentsMapping argsMapping, TypeEvalContext context) {
        PyElement callSite = argsMapping.getCallSite();
        PyExpression callSiteExpression = callSite instanceof PyExpression expression ? expression : null;
        PyCallableType callableType = argsMapping.getCallableType();
        SubstitutionsIdentifier si = callSiteExpression == null ? null : new SubstitutionsIdentifier(callSiteExpression, callableType);
        PyTypeChecker.GenericSubstitutions solution = enterCsp(si, context);
        return solution != null ? solution : new PyTypeChecker.GenericSubstitutions();
    }

    public static PyTypeChecker.@Nullable GenericSubstitutions unifyGenericCall(@Nullable PyElement callSite,
                                                                                @Nullable PyCallableType callableType,
                                                                                Map<PyCallableArgument, PyCallableParameter> mappedParameters,
                                                                                TypeEvalContext context) {
        PyExpression callSiteExpression = callSite instanceof PyExpression expression ? expression : null;
        SubstitutionsIdentifier si = callSiteExpression == null ? null : new SubstitutionsIdentifier(callSiteExpression, callableType);
        PyTypeChecker.GenericSubstitutions solution = enterCsp(si, context);
        return solution != null ? solution : PyTypeChecker.unifyGenericCall(mappedParameters, context);
    }

    /**
     * It is beneficial to solve nested CSPs in a single CSP at once instead of solving them independently.
     * The reason is that having a combined CSP that includes all nested CSPs allows for a better solution that fits all constraints.
     * <p>
     * Nested CSPs can occur already in simple examples like shown below:
     * <pre>{@code
     * class A: ...
     * class B(A): ...
     * def f[T](t: T) -> T: ...
     * x: A = f(B()) # no nesting, but CSP necessary for type inference
     * y: list[A] = [B()] # no nesting, but CSP necessary for type inference
     * z1: list[A] = f([B()]) # nesting of two CSPs
     * z2: list[A] = [f(B())] # nesting of two CSPs
     * }</pre>
     * <p>
     * To deal with nested CSPs, we first identify the top-most CSP. When creating its CSP,
     * also constraints from all nested CSPs will be included. Recursive calls to {@link #enterCsp}
     * during CSP creation and solving will return only an empty {@link PyTypeChecker.GenericSubstitutions}.
     *
     * @return null if a fallback approach is necessary
     */
    private static PyTypeChecker.@Nullable GenericSubstitutions enterCsp(@Nullable SubstitutionsIdentifier si, TypeEvalContext context) {
        if (si == null) {
            return null; // use old approach
        }
        if (!Registry.is("python.use.csp.type.inference", true)) {
            return null; // use old approach
        }
        if (!(context instanceof TypeEvalContextImpl contextImpl)) {
            return null; // use old approach
        }

        SubstitutionsIdentifier topCsp = getTopCsp(si, contextImpl);
        if (topCsp == null) {
            return null; // use old approach
        }

        PyTypeChecker.GenericSubstitutions cachedSubstitutions = contextImpl.getKnownSubstitutions(si);
        if (cachedSubstitutions != null) {
            return cachedSubstitutions; // return cached solution
        }

        PyTypeChecker.GenericSubstitutions cachedSubstitutionsTopCsp = contextImpl.getKnownSubstitutions(topCsp);
        if (cachedSubstitutionsTopCsp != null) {
            // suspicious: ensure that `findParentCsp()` and `findChildCsp()` are round-tripable
            return null; // fallback to old approach
        }

        PyTypeChecker.GenericSubstitutions temporarySubstitutions = new PyTypeChecker.GenericSubstitutions();

        boolean onRecursionStack = recursionGuard.currentStack().contains(topCsp.expression());
        if (onRecursionStack) {
            return temporarySubstitutions; // early exit in case of recursion
        }

        // Use a CSP-shared TemporaryContext to avoid pollution of original context with intermediate results
        TypeEvalContextImpl.TemporaryContext tmpContext = contextImpl instanceof TypeEvalContextImpl.TemporaryContext temporaryContext
            ? temporaryContext
            : new TypeEvalContextImpl.TemporaryContext(contextImpl, true);
        TypeEvalContextImpl nonTmpContext = contextImpl instanceof TypeEvalContextImpl.TemporaryContext temporaryContext
            ? temporaryContext.getMyParent()
            : contextImpl;

        CspBuilder builder = recursionGuard.doPreventingRecursion(topCsp.expression(), false, () ->
            buildAndSolveCsp(topCsp, tmpContext)
        ); // returns also null in case of recursion

        PyCspSolution solution = builder != null ? builder.getSolution() : null;
        if (solution == null) {
            return null;
        }
        if (solution.failed()) {
            return null; // TODO: return instead a partly solution
        }

        for (Map.Entry<SubstitutionsIdentifier, PyTypeChecker.GenericSubstitutions> entry : builder.getAllSubstitutions()) {
            SubstitutionsIdentifier nestedSi = entry.getKey();
            PyTypeChecker.GenericSubstitutions substitutions = entry.getValue();
            Map<PyTypeParameterType, Ref<@Nullable PyType>> nestedInstantiations = solution.instantiations().get(nestedSi);
            PyTypeChecker.GenericSubstitutions solvedSubstitutions =
                substitutions.addToCopy(nestedInstantiations != null ? nestedInstantiations : Collections.emptyMap());
            PyTypeChecker.GenericSubstitutions simplifiedSubstitutions = solvedSubstitutions.simplify(context);
            nonTmpContext.putSubstitutions(nestedSi, simplifiedSubstitutions);
        }

        return nonTmpContext.getKnownSubstitutions(si);
    }

    // TODO: wrong parameter mapping passed by testExplicitlyParameterizedGenericConstructorCall: self missing?


    private static @Nullable CspBuilder buildAndSolveCsp(SubstitutionsIdentifier si, TypeEvalContextImpl tmpContext) {
        try {
            CspBuilder builder = new CspBuilder(tmpContext);
            PyType returnType;
            if (si.expression() instanceof PyCallExpression) {
                returnType = buildCallSiteExpressionCsp(si, builder, tmpContext);
            }
            else if (si.expression() instanceof PySequenceExpression sequenceExpression) {
                returnType = buildSequenceExpressionCsp(new SubstitutionsIdentifier(sequenceExpression), builder, tmpContext);
            }
            else {
                throw new NotSupportedException();
            }

            PyType expectedType = PyExpectedTypeJudgement.getExpectedType(si.expression(), tmpContext);
            if (expectedType != null && !isAnyOrUnknown(expectedType) && !isAnyOrUnknown(returnType)) {
                builder.addConstraint(returnType, expectedType, PyVariance.COVARIANT, ConstraintPriority.LOW);
            }

            builder.solve();

            return builder;
        }
        catch (NotSupportedException e) {
            return null;
        }
    }

    private static @Nullable PyType buildNestedCsp(PyExpression expression, CspBuilder builder, TypeEvalContext context) {
        PyExpression actualCspAnchor = findChildCsp(expression);
        if (!isCsp(actualCspAnchor)) {
            if (actualCspAnchor instanceof PyTypedElement) {
                PyType anchorType = PyTypeInferenceCspFactoryKt.getNarrowedType(context, actualCspAnchor);
                return PyTypeInferenceCspFactoryKt.normalizeType(anchorType, context);
            }
            else {
                return null;
            }
        }

        if (actualCspAnchor instanceof PyCallExpression callExpression) {
            return buildCallSiteExpressionCsp(callExpression, builder, context);
        }
        else if (actualCspAnchor instanceof PySequenceExpression sequenceExpression) {
            return buildSequenceExpressionCsp(new SubstitutionsIdentifier(sequenceExpression), builder, context);
        }
        else {
            throw new NotSupportedException();
        }
    }

    private static PyType buildSequenceExpressionCsp(SubstitutionsIdentifier si, CspBuilder builder, TypeEvalContext context) {
        if (!(si.expression() instanceof PySequenceExpression seqExpr)) {
            throw new NotSupportedException();
        }
        PyType genericType = PyCollectionTypeUtil.findGenericDefinitionTypeOfSequence(seqExpr, context);
        if (!(PyTypeInferenceCspFactoryKt.normalizeType(genericType, context) instanceof PyClassType sequenceType)) {
            throw new NotSupportedException();
        }
        PyTypeChecker.GenericSubstitutions cspSubstitutions = PyTypeChecker.unifyReceiver(sequenceType, context);
        builder.putSubstitutions(si, cspSubstitutions);

        List<Map.Entry<PyTypeVarType, List<PyExpression>>> elementsPerTypeVar = mapElementsToTypeVars(seqExpr, sequenceType, cspSubstitutions);
        List<@Nullable PyType> typeArgumentIVs = new ArrayList<>();
        for (Map.Entry<PyTypeVarType, List<PyExpression>> entry : elementsPerTypeVar) {
            PyInferenceVariable iv = builder.addInferenceVariable(entry.getKey(), si);
            typeArgumentIVs.add(iv);
            List<@Nullable PyType> elementTypes = ContainerUtil.map(entry.getValue(), element -> buildNestedCspAndUnpack(seqExpr, element, builder, context));
            builder.addConstraint(iv, PyUnionType.unionOrUnknown(elementTypes), PyVariance.CONTRAVARIANT, ConstraintPriority.MEDIUM);
        }

        if (seqExpr instanceof PyTupleExpression) {
            PyTupleType tupleType = PyTupleType.create(seqExpr, typeArgumentIVs);
            if (tupleType == null) {
                throw new NotSupportedException();
            }
            return tupleType;
        }
        return new PyCollectionTypeImpl(sequenceType.getPyClass(), false, typeArgumentIVs);
    }

    private static @Nullable PyType buildNestedCspAndUnpack(PySequenceExpression expression,
                                                            PyExpression element,
                                                            CspBuilder builder,
                                                            TypeEvalContext context) {
        PyType nestedReturnType = buildNestedCsp(element, builder, context);
        if (nestedReturnType instanceof PyUnpackedTupleType unpackedTupleType && !(expression instanceof PyTupleExpression)) {
            return PyUnionType.unionOrUnknown(unpackedTupleType.getElementTypes());
        }
        // An element without an expression to type, such as `(` with no body in incomplete code
        return nestedReturnType != null ? nestedReturnType : com.jetbrains.python.psi.types.PyAnyType.getUnknown();
    }

    /**
     * Maps the element expressions of a sequence literal to the type variables retrieved from the receiver {@code substitutions}.
     * <p>
     * List and set literals have only a single type variable to which all (unpacked) elements are mapped.
     * Tuple literals are flattened via {@link PyCollectionTypeUtil#flattenSequenceElements}; the flattened element expressions appear
     * in the same positional order in which the tuple's type parameter is expanded to positional copies {@code _T_co#i}
     * (see {@link PyTypeChecker#expandTupleTypeParameters}), so the element at position {@code i} maps to the type variable of position {@code i}.
     */
    private static List<Map.Entry<PyTypeVarType, List<PyExpression>>> mapElementsToTypeVars(PySequenceExpression expression,
                                                                                         PyClassType sequenceType,
                                                                                         PyTypeChecker.GenericSubstitutions substitutions) {
        List<PyTypeVarType> ownTypeVars =
            ContainerUtil.filter(substitutions.getTypeVars().keySet(), it -> sequenceType.getPyClass().equals(it.getScopeOwner()));
        if (expression instanceof PyTupleExpression tupleExpression) {
            List<PyExpression> flattenedElements = PyCollectionTypeUtil.flattenSequenceElements(tupleExpression);
            if (flattenedElements.isEmpty() || flattenedElements.size() > 10) {
                throw new NotSupportedException();
            }
            // An unflattenable splice (`*iterable`) breaks the positional mapping between expressions and type variables
            if (ContainerUtil.exists(flattenedElements, it -> it instanceof PyStarExpression)) {
                throw new NotSupportedException();
            }
            // TypeVars of tuples are expanded into `_T_co#i` for each position `i`
            List<PyTypeVarType> positionalTypeVars = new ArrayList<>();
            for (PyType typeArgument : sequenceType.getTypeArguments()) {
                if (typeArgument instanceof PyTypeVarType typeVarType) {
                    positionalTypeVars.add(typeVarType);
                }
            }
            if (positionalTypeVars.size() != flattenedElements.size()) {
                throw new NotSupportedException();
            }
            List<Map.Entry<PyTypeVarType, List<PyExpression>>> result = new ArrayList<>();
            for (int i = 0; i < positionalTypeVars.size(); i++) {
                result.add(Map.entry(positionalTypeVars.get(i), List.of(flattenedElements.get(i))));
            }
            return result;
        }
        else if (expression instanceof PyListLiteralExpression || expression instanceof PySetLiteralExpression) {
            // lists and sets have only a single type variable
            if (ownTypeVars.size() != 1) {
                throw new NotSupportedException();
            }
            PyTypeVarType typeVar = ownTypeVars.get(0);
            List<PyExpression> elements = Lists.getFirstItems(PyCollectionTypeUtil.flattenSequenceElements(expression), 10);
            return List.of(Map.entry(typeVar, elements));
        }
        else {
            throw new NotSupportedException();
        }
    }

    private static @Nullable PyType buildCallSiteExpressionCsp(PyCallSiteExpression callSite, CspBuilder builder, TypeEvalContext context) {
        if (!(callSite instanceof PyCallExpression callExpression)) {
            throw new NotSupportedException();
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<PyCallableType> callableTypes = callExpression.multiResolveCallee(resolveContext);
        List<@Nullable PyType> returnTypes = new ArrayList<>();
        boolean allNotSupported = !callableTypes.isEmpty();
        for (PyCallableType callableType : callableTypes) {
            try {
                PyType returnType = buildCallSiteExpressionCsp(new SubstitutionsIdentifier(callExpression, callableType), builder, context);
                returnTypes.add(returnType);
                allNotSupported = false;
            }
            catch (NotSupportedException ignored) {
            }
        }
        if (allNotSupported) {
            throw new NotSupportedException();
        }
        return PyUnionType.unionOrUnknown(returnTypes);
    }

    private static @Nullable PyType buildCallSiteExpressionCsp(SubstitutionsIdentifier si, CspBuilder builder, TypeEvalContext context) {
        PyExpression callSite = si.expression();
        PyCallableType callableType = si.callableType();
        if (!(callSite instanceof PyCallExpression callExpression)) {
            throw new NotSupportedException();
        }
        if (callableType == null) {
            throw new NotSupportedException();
        }
        PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArgumentsWithDefaults(callExpression, callableType, context);
        if (!mapping.isComplete()) {
            throw new NotSupportedException();
        }
        Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();
        PyTypeChecker.GenericSubstitutions cspSubstitutions = new PyTypeChecker.GenericSubstitutions();
        PyType declaredReturn = callableType.getReturnType(context);

        builder.putSubstitutions(si, cspSubstitutions);
        // find type variables
        HashMap<PyTypeVarType, @Nullable Ref<@Nullable PyType>> ivSubstitutionMap = new HashMap<>();
        PyTypeChecker.Generics generics = PyTypeChecker.collectGenerics(callableType, context);
        if (!generics.getParamSpecs().isEmpty() || !generics.getTypeVarTuples().isEmpty()) {
            throw new NotSupportedException();
        }
        for (PyTypeVarType typeVarEntry : generics.getTypeVars()) {
            PyInferenceVariable iv = builder.addInferenceVariable(typeVarEntry, si);
            ivSubstitutionMap.put(typeVarEntry, Ref.create(iv));
            ensureBoundsAndConstraints(builder, typeVarEntry, iv, cspSubstitutions, context);
        }
        PyTypeChecker.GenericSubstitutions ivSubstitutions = cspSubstitutions.addToCopy(ivSubstitutionMap, null, null);

        // type arguments
        for (Map.Entry<PyTypeVarType, @Nullable Ref<@Nullable PyType>> entry : cspSubstitutions.getTypeVars().entrySet()) {
            Ref<@Nullable PyType> value = entry.getValue();
            if (value != null) {
                PyType keyIV = PyTypeChecker.substitute(entry.getKey(), ivSubstitutions, context);
                PyType valueIV = PyTypeChecker.substitute(value.get(), ivSubstitutions, context);
                builder.addConstraint(keyIV, valueIV, PyVariance.INVARIANT, ConstraintPriority.HIGH);
            }
        }

        // function arguments
        for (Map.Entry<PyExpression, PyCallableParameter> entry : mappedParameters.entrySet()) {
            PyExpression argument = entry.getKey();
            PyCallableParameter parameter = entry.getValue();
            if (parameter.isKeywordContainer()) {
                throw new NotSupportedException();
            }

            PyType nestedReturnType = buildNestedCsp(argument, builder, context);
            PyType expectedParameterType = parameter.getArgumentType(context);
            PyType passedArgumentType = nestedReturnType != null
                ? nestedReturnType
                : getArgumentType(parameter, argument, expectedParameterType, ivSubstitutions, context);

            if (!PyTypeUtilKt.isUnknown(expectedParameterType)
                && (hasGenericsOrIVs(expectedParameterType, context) || hasGenericsOrIVs(passedArgumentType, context))
            ) {
                PyType expectedParamTypeSelfIV = PyTypeChecker.substitute(expectedParameterType, ivSubstitutions, context);
                PyType passedArgumentTypeIV = PyTypeChecker.substitute(passedArgumentType, ivSubstitutions, context);
                // semantics: Actual <: TV
                builder.addConstraint(expectedParamTypeSelfIV, passedArgumentTypeIV, PyVariance.CONTRAVARIANT, ConstraintPriority.MEDIUM);
            }
        }

        // return type
        return PyTypeChecker.substitute(declaredReturn, ivSubstitutions, context);
    }

    public static boolean hasGenericsOrIVs(@Nullable PyType type, TypeEvalContext context) {
        if (type instanceof PyInferenceVariable) {
            return true;
        }
        if (type instanceof PyTypeParameterType) {
            return true;
        }
        if (type instanceof PyClassType classType && ContainerUtil.exists(classType.getTypeArguments(), it -> it instanceof PyInferenceVariable)) {
            return true;
        }
        if (PyTypeChecker.hasGenerics(type, context)) {
            return true;
        }
        return false;
    }

    private static @Nullable PyType getArgumentType(PyCallableParameter parameter,
                                                    PyExpression argument,
                                                    @Nullable PyType paramType,
                                                    PyTypeChecker.GenericSubstitutions substitutions,
                                                    TypeEvalContext context) {
        PyType promotedToLiteral = PyLiteralType.promoteToLiteral(argument, paramType, context, substitutions);
        PyType actualArgType = !PyTypeUtilKt.isUnknown(promotedToLiteral) ? promotedToLiteral : context.getType(argument);
        PyType argTypeSelfInstantiated = parameter.isSelf() && actualArgType instanceof PyClassLikeType classLikeType && classLikeType.isDefinition()
            ? classLikeType.toInstance()
            : actualArgType;
        PyType normalizedType = PyTypeInferenceCspFactoryKt.normalizeType(argTypeSelfInstantiated, context);
        return normalizedType;
    }

    private static void ensureBoundsAndConstraints(CspBuilder builder,
                                                   PyTypeParameterType tv,
                                                   PyInferenceVariable iv,
                                                   PyTypeChecker.GenericSubstitutions ivGenericSubstitutions,
                                                   TypeEvalContext context) {
        if (!(tv instanceof PyTypeVarType typeVar)) {
            return;
        }

        // bounds
        PyType bound = typeVar.getBound();
        if (bound != null && !isAnyOrUnknown(bound)) {
            PyType typeVarBoundIV = PyTypeChecker.substitute(bound, ivGenericSubstitutions, context);
            // semantics: TV <: Bound
            builder.addConstraint(iv, typeVarBoundIV, PyVariance.COVARIANT, ConstraintPriority.HIGH, false);
        }
        else if (!typeVar.getConstraints().isEmpty()) {
            // Note: The Python type variable constraint(s) cannot be fully modeled without a specific CSP constraint that would model a strict logical OR.
            // A logical OR does unfortunately come with a performance impact since it makes backtracking during the solving process inevitable.
            // As a solution, Python type variable constraints will be modeled using an approximation that ensures that the type variable is both
            // (1) a subtype of the given constraints and (2) a supertype of the given constraints. The former is done modeling the tv-constraints
            // as a union type, and the latter is done modeling the tv-constraints as an intersection type.
            // Only at the very end, during instantiation, an actual set of remaining tv-constraints is chosen.
            // Note that both of these bounds are necessary to ensure that the TV will be instantiated as exactly one of the given tv-constraints
            // and not as a subtype of one of the given tv-constraints.
            List<@Nullable PyType> constraintsIV =
                ContainerUtil.map(typeVar.getConstraints(), it -> PyTypeChecker.substitute(it, ivGenericSubstitutions, context));
            PyType intersectionOfConstraints = PyIntersectionType.intersectionOrTop(constraintsIV);
            PyType unionOfConstraints = PyUnionType.unionOrUnknown(constraintsIV);
            // semantics: TV approximates CV_1 ⊕ CV_2 ⊕ ... ⊕ CV_n by
            // CV_1 & CV_2 & ... & CV_n <: TV <: CV_1 | CV_2 | ... | CV_n
            builder.addConstraint(iv, intersectionOfConstraints, PyVariance.CONTRAVARIANT, ConstraintPriority.HIGH, false);
            builder.addConstraint(iv, unionOfConstraints, PyVariance.COVARIANT, ConstraintPriority.HIGH, false);
        }
    }

    public static @Nullable PyExpression findChildCsp(@Nullable PyExpression expression) {
        if (expression instanceof PyParenthesizedExpression parenthesizedExpression) {
            return findChildCsp(parenthesizedExpression.getContainedExpression());
        }
        else if (expression instanceof PyKeywordArgument keywordArgument) {
            return findChildCsp(keywordArgument.getValueExpression());
        }
        else {
            return expression;
        }
    }

    public static @Nullable PsiElement findParentCsp(PsiElement expression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PyCallExpression) {
            if (expression instanceof PyArgumentList || expression instanceof PyKeywordArgument) {
                return parent;
            }
            else {
                return null;
            }
        }
        else if (parent instanceof PyArgumentList || parent instanceof PyKeywordArgument || parent instanceof PyParenthesizedExpression) {
            return findParentCsp(parent);
        }
        else if (isCsp(parent)) {
            return parent;
        }
        else {
            return null;
        }
    }

    public static @Nullable SubstitutionsIdentifier getTopCsp(SubstitutionsIdentifier si, TypeEvalContext context) {
        PyExpression top = null;
        PyExpression cursor = si.expression();
        while (isCsp(cursor)) {
            top = cursor;
            PsiElement parent = findParentCsp(cursor);
            if (!(parent instanceof PyExpression parentExpression)) {
                break;
            }
            cursor = parentExpression;
        }
        if (top == null) {
            return null;
        }
        if (top == si.expression()) {
            return si;
        }
        if (top instanceof PyCallExpression callExpression) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
            PyCallableType callableType = ContainerUtil.getFirstItem(callExpression.multiResolveCallee(resolveContext));
            if (callableType == null) {
                return null;
            }
            return new SubstitutionsIdentifier(callExpression, callableType);
        }
        return new SubstitutionsIdentifier(top);
    }

    private static boolean isCsp(@Nullable PsiElement expression) {
        if (expression instanceof PyCallExpression ||
            expression instanceof PyDecoratorList ||
            expression instanceof PyListLiteralExpression ||
            expression instanceof PySetLiteralExpression ||
            expression instanceof PyTupleExpression) {
            return true;
        }
        return false;
    }
}
