// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.StackOverflowPreventedException;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PyExpectedTypeJudgement {
    private static final RecursionGuard<Pair<PsiElement, TypeEvalContext>> recursionGuard =
        RecursionManager.createGuard("PyExpectedTypeJudgement");
    private static final Key<Boolean> SYNTHETIC_PROTOCOL = Key.create("PyExpectedTypeJudgement.SYNTHETIC_PROTOCOL");

    private PyExpectedTypeJudgement() {
    }

    /**
     * Computes the expected type of the given expression from its usage in the AST.
     * The expected type is either the explicitly declared type (i.e., type annotation) that constraints the given expression.
     * Or the expected type is implied by the grammar and language semantics, e.g., for varargs.
     * Note that in some cases the expected type has a circular dependency to itself via type inference, which in turn calls the
     * expected type judgment again. This can happen e.g., for cases related to generic functions.
     * <p>
     * Supported root AST elements:
     * - Argument to a call (positional or keyword)
     * - Argument to indexed access (i.e., subscription expression)
     * - RHS of an assignment
     * - Yield expression value (RHS)
     * - Return statement value
     * <p>
     * When given an AST element that is a child C of a root AST element, this method traverses the parent chain upwards
     * to compute the expected type of the root element. Based on that, it tries to conclude the expected type of C.
     * <p>
     * Returns null iff no type declaration, or {@code Any} was found.
     * <p>
     * Note: At the moment, this method does not resolve subtype relationships in cases where the AST expectations differ
     * from the actual type found. E.g., consider the example {@code my_var : MyListOfInts = [1]} and suppose that the expected
     * type of {@code 1} is requested. This implementation does not (yet) resolve {@code MyListOfInts} to compute its relation to the
     * implied supertype {@code List}, to then retrieve the type of {@code List}s type parameter.
     */
    public static @Nullable PyType getExpectedType(PyExpression expr, TypeEvalContext ctx) {
        try {
            return recursionGuard.doPreventingRecursion(Pair.create(expr.getParent(), ctx), false, () ->
                doGetExpectedType(expr, ctx)
            );
        }
        catch (StackOverflowPreventedException e) {
            return PyAnyType.getUnknown();
        }
    }

    public static @Nullable PyType doGetExpectedType(PyExpression expr, TypeEvalContext ctx) {
        // Traverse the AST upwards to find the root expression (either assignment, function call, return statement)
        // Do this recursively to easily map the result type to the original sub-element.
        // Example: x2: str; x1, (x2, x3) = (42, (expr, "spam")) # expr is the requested sub-element, the whole tuple is the root expression

        PsiElement parent = expr.getParent();
        if (parent instanceof PyStarArgument || parent instanceof PyParenthesizedExpression) {
            return getExpectedType((PyExpression) parent, ctx);
        }
        else if (parent instanceof PyAssignmentExpression assignmentExpression) {
            PyType expectedType = fromWalrus(expr, ctx);
            if (expectedType != null && !PyTypeUtilKt.isUnknown(expectedType)) {
                return expectedType;
            }
            return getExpectedType(assignmentExpression, ctx);
        }
        else if (parent instanceof PySliceItem) {
            PyBuiltinCache cache = PyBuiltinCache.getInstance(expr);
            return PyUnionType.unionOrUnknown(cache.getIntType(), cache.getNoneType());
        }
        else if (parent instanceof PyStarExpression) {
            if (parent.getParent() instanceof PyExpression starParent) {
                PyType typeOfStarParent = getExpectedType(starParent, ctx);
                if (typeOfStarParent != null && PyNames.ITERABLE.equals(typeOfStarParent.getName())) {
                    return typeOfStarParent;
                }
                if (typeOfStarParent instanceof PyClassType classType && (classType instanceof PyTupleType || classType.isParameterized())) {
                    // upcast to Iterable
                    return createIterableType(expr, PyTypeChecker.getIteratedItemType(classType, ctx));
                }
            }
            return null;
        }
        else if (parent instanceof PyDoubleStarExpression) {
            if (parent.getParent() instanceof PyExpression doubleStarParent) {
                PyType typeOfDoubleStarParent = getExpectedType(doubleStarParent, ctx);
                if (typeOfDoubleStarParent != null && PyNames.MAPPING.equals(typeOfDoubleStarParent.getName())) {
                    return typeOfDoubleStarParent;
                }
                if (typeOfDoubleStarParent instanceof PyClassType classType && classType.isParameterized()) {
                    // upcast to Map
                    return PyCollectionTypeImpl.createTypeByQName(expr, "typing." + PyNames.MAPPING, false, classType.getTypeArguments());
                }
            }
            return null;
        }
        else if (parent instanceof PyKeywordArgument keywordArgument) {
            if (expr.equals(keywordArgument.getValueExpression())) {
                return getExpectedType(keywordArgument, ctx);
            }
            return null;
        }
        else if (parent instanceof PyTupleExpression tupleExpression) {
            int indexOfExpr = ArrayUtil.indexOf(tupleExpression.getElements(), expr);
            PyType typeOfParentTuple = getExpectedType(tupleExpression, ctx);
            if (typeOfParentTuple instanceof PyTupleType tupleType && !tupleType.getElementTypes().isEmpty()) {
                return getElementTypeAtTupleIndex(tupleExpression, tupleType, indexOfExpr);
            }
            if (typeOfParentTuple instanceof PyClassType classType && classType.isParameterized()) {
                return PyTypeChecker.getIteratedItemType(classType, ctx);
            }
            return null;
        }
        else if (parent instanceof PySetLiteralExpression || parent instanceof PyListLiteralExpression) {
            PyType typeOfParentList = getExpectedType((PyExpression) parent, ctx);
            if (typeOfParentList instanceof PyClassType classType && classType.isParameterized()) {
                return PyTypeChecker.getIteratedItemType(classType, ctx);
            }
            return null;
        }
        else if (parent instanceof PyKeyValueExpression keyValueExpression) {
            PsiElement grandParent = keyValueExpression.getParent();
            PyType typeOfParentDict;
            if (grandParent instanceof PyDictLiteralExpression dictLiteralExpression) {
                typeOfParentDict = getExpectedType(dictLiteralExpression, ctx);
            }
            else if (grandParent instanceof PyDictCompExpression dictCompExpression && dictCompExpression.getResultExpression() == keyValueExpression) {
                typeOfParentDict = getExpectedType(dictCompExpression, ctx);
            }
            else {
                return null;
            }
            if (typeOfParentDict instanceof PyClassType classType && classType.getTypeArguments().size() == 2) {
                int index = expr.equals(keyValueExpression.getKey()) ? 0 : 1;
                return classType.getTypeArguments().get(index);
            }
            if (typeOfParentDict instanceof PyUnpackedTypedDictType unpackedTypedDictType &&
                keyValueExpression.getKey() instanceof PyStringLiteralExpression stringLiteralExpression) {
                String argName = stringLiteralExpression.getStringValue();
                return unpackedTypedDictType.getTypedDictType().getElementType(argName, ctx);
            }
            return null;
        }
        else if (parent instanceof PyNamedParameter namedParameter) {
            PyExpression annotationValue = namedParameter.getAnnotation() != null ? namedParameter.getAnnotation().getValue() : null;
            if (expr == namedParameter.getDefaultValue() && annotationValue != null) {
                return PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.getType(annotationValue, ctx));
            }
        }
        else if (parent instanceof PyParameterList parameterList) {
            if (expr.getParent().getParent() instanceof PyLambdaExpression lambdaExpression && expr instanceof PyParameter parameter) {
                int indexOfExpr = ArrayUtil.indexOf(parameterList.getParameters(), parameter);
                PyType typeOfParentLambda = getExpectedType(lambdaExpression, ctx);

                if (typeOfParentLambda instanceof PyUnionType unionType) {
                    // match first callable
                    typeOfParentLambda = ContainerUtil.find(unionType.getMembers(),
                                                            it -> it instanceof PyCallableType callableType && callableType.isCallable());
                }
                if (typeOfParentLambda instanceof PyCallableType callableType) {
                    List<PyCallableParameter> parameters = callableType.getParameters(ctx);
                    if (parameters != null && indexOfExpr >= 0 && indexOfExpr < parameters.size()) {
                        return parameters.get(indexOfExpr).getType(ctx);
                    }
                }
            }
            return null;
        }
        else if (parent instanceof PyLambdaExpression lambdaExpression) {
            PyType typeOfParentLambda = getExpectedType(lambdaExpression, ctx);
            if (typeOfParentLambda instanceof PyCallableType callableType) {
                return callableType.getReturnType(ctx);
            }
            return null;
        }
        else if (parent instanceof PyListCompExpression || parent instanceof PySetCompExpression || parent instanceof PyGeneratorExpression) {
            PyComprehensionElement comprehension = (PyComprehensionElement) parent;
            if (comprehension.getResultExpression() == expr) {
                PyType expectedComprehensionType = getExpectedType((PyExpression) comprehension, ctx);
                if (expectedComprehensionType == null) {
                    return null;
                }
                if (expectedComprehensionType instanceof PyClassType classType && classType.isParameterized()) {
                    return ContainerUtil.getFirstItem(classType.getTypeArguments());
                }
            }
        }
        else if (parent instanceof PyBinaryExpression binaryExpression) {
            return fromBinaryExpression(expr, binaryExpression, ctx);
        }

        // Compute the expected type from a given root statement/expression
        PyType result = fromArgument(expr, ctx);
        if (result == null) {
            result = fromAssignment(expr, ctx);
        }
        if (result == null) {
            result = fromYield(expr, ctx);
        }
        if (result == null) {
            result = fromReturn(expr, ctx);
        }
        if (result == null) {
            result = PyAnyType.getUnknown();
        }
        return result;
    }

    private static @Nullable PyType fromArgument(PyExpression callArgument, TypeEvalContext ctx) {
        PyElement callSite;
        if (callArgument.getParent() instanceof PyArgumentList argumentList && argumentList.getParent() instanceof PyCallExpression callExpression) {
            callSite = callExpression;
        }
        else if (callArgument.getParent() instanceof PySubscriptionExpression subscriptionExpression) {
            callSite = subscriptionExpression;
        }
        else {
            return null;
        }

        List<PyCallExpression.PyArgumentsMapping> argMappings = PyCallExpressionHelper.mapArguments(callSite, PyResolveContext.defaultContext(ctx));
        Set<@Nullable PyType> argTypes = new LinkedHashSet<>();

        for (PyCallExpression.PyArgumentsMapping mapping : argMappings) {
            Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();

            PyType paramType;
            if (callArgument instanceof PyStarArgument starArgument && callSite instanceof PyCallExpression) {
                paramType = fromStarArgument(starArgument, mapping, ctx);
            }
            else {
                PyCallableParameter param = mappedParameters.get(callArgument);
                if (param == null) {
                    continue;
                }

                PyType paramTypeOrUnpacked = param.getArgumentType(ctx);
                if (paramTypeOrUnpacked instanceof PyUnpackedTupleType unpackedTupleType) {
                    // happens here: f(1, "s") for function: `def f(*args: *tuple[int,str]): pass`
                    if (unpackedTupleType.isUnbound()) {
                        paramType = ContainerUtil.getFirstItem(unpackedTupleType.getElementTypes());
                    }
                    else {
                        int paramIdx = new ArrayList<>(mappedParameters.keySet()).indexOf(callArgument);
                        List<PyType> elementTypes = unpackedTupleType.getElementTypes();
                        paramType = paramIdx >= 0 && paramIdx < elementTypes.size() ? elementTypes.get(paramIdx) : null;
                    }
                }
                else {
                    paramType = paramTypeOrUnpacked;
                }
            }

            argTypes.add(substituteTypeVars(paramType, mapping, ctx));
        }

        return PyUnionType.unionOrUnknown(argTypes);
    }

    private static @Nullable PyType fromStarArgument(PyStarArgument callArgument, PyCallExpression.PyArgumentsMapping mapping, TypeEvalContext ctx) {
        Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();
        if (callArgument.isKeyword()) {
            PyCallableParameter kwargsContainer = ContainerUtil.find(mappedParameters.values(), cp -> cp.isKeywordContainer());
            PyType kwargsContainerType = kwargsContainer != null ? kwargsContainer.getType(ctx) : null;

            List<PyCallableParameter> extraParams = ContainerUtil.filter(mapping.getParametersMappedToVariadicKeywordArguments(), parameter -> {
                String name = parameter.getName();
                return name != null && !parameter.isSelf() && !parameter.isPositionalContainer() && !parameter.isKeywordContainer();
            });

            if (extraParams.isEmpty() && kwargsContainer != null) {
                return kwargsContainerType;
            }

            // TODO For non-variadic types of **kwargs requires supporting extra_items in PyTypedDictType PY-85421
            Map<String, PyTypedDictType.FieldTypeAndTotality> baseFields = kwargsContainerType instanceof PyUnpackedTypedDictType unpackedTypedDictType
                ? unpackedTypedDictType.getTypedDictType().fields(ctx)
                : Collections.emptyMap();
            Map<String, PyTypedDictType.FieldTypeAndTotality> fields = new LinkedHashMap<>();
            fields.putAll(baseFields);
            for (PyCallableParameter parameter : extraParams) {
                String name = parameter.getName();
                if (name == null) {
                    continue;
                }
                if (fields.containsKey(name)) {
                    continue;
                }
                fields.put(name, new PyTypedDictType.FieldTypeAndTotality(
                    null, // We define a schema, not a specific instance value
                    parameter.getType(ctx),
                    new PyTypedDictType.TypedDictFieldQualifiers(!parameter.hasDefaultValue())
                ));
            }

            PyClass dictClass = PyBuiltinCache.getInstance(callArgument).getClass("dict");
            if (dictClass == null) {
                return null;
            }
            // TODO: This is incorrect:
            // `PyTypedDict` declaration is either a `PyClass` node:
            // ```
            // class TD(typing.TypedDict):
            //   ...
            // ```
            //
            // or a `PyTargetExpression` node:
            // ```
            // TD = typing.TypedDict("TD", {})
            // ```
            PyCallableType mappingCallableType = mapping.getCallableType();
            var declaration = mappingCallableType != null ? mappingCallableType.getCallable() : null;
            if (declaration == null) {
                return null;
            }
            PyTypedDictType typedDictType = new PyTypedDictType(
                "Parameters",
                context -> fields,
                dictClass,
                false,
                declaration
            );
            return new PyUnpackedTypedDictTypeImpl(typedDictType);
        }
        else {
            PyCallableParameter param = ContainerUtil.find(mappedParameters.values(), cp -> cp.isPositionalContainer());
            if (param == null) {
                // The function declares no varargs, but the caller passed a starred expression:
                // E.g.: def f(s: str, n: int) gets called f(*("foo", 123)).

                List<@Nullable PyType> paramTypes = ContainerUtil.map(mapping.getParametersMappedToVariadicPositionalArguments(), cp -> cp.getType(ctx));
                return PyTupleType.create(callArgument, paramTypes);
            }
            else {
                return param.getType(ctx);
            }
        }
    }

    private static @Nullable PyType substituteTypeVars(@Nullable PyType paramType,
                                                       PyCallExpression.PyArgumentsMapping mapping,
                                                       TypeEvalContext ctx) {
        if (!PyTypeChecker.hasGenerics(paramType, ctx)) {
            return paramType;
        }
        PyTypeChecker.GenericSubstitutions substitutions = PyTypeInferenceCspFactory.unifyReceiver(mapping, ctx);
        return PyTypeChecker.substitute(paramType, substitutions, ctx);
    }

    private static @Nullable PyType fromWalrus(PyExpression expr, TypeEvalContext ctx) {
        if (!(expr.getParent() instanceof PyAssignmentExpression parent)) {
            return null;
        }
        if (!expr.equals(parent.getAssignedValue())) {
            return null;
        }
        PyExpression lhs = parent.getTarget();
        if (lhs == null) {
            return null;
        }
        PyExpression rhs = parent.getAssignedValue();
        if (rhs == null) {
            return null;
        }
        return fromLhs(lhs, rhs, ctx);
    }

    private static @Nullable PyType fromAssignment(PyExpression expr, TypeEvalContext ctx) {
        if (!(expr.getParent() instanceof PyAssignmentStatement parent)) {
            return null;
        }
        if (!expr.equals(parent.getAssignedValue())) {
            return null;
        }
        PyExpression lhs = parent.getLeftHandSideExpression();
        if (lhs == null) {
            return null;
        }
        PyExpression rhs = parent.getAssignedValue();
        if (rhs == null) {
            return null;
        }
        return fromLhs(lhs, rhs, ctx);
    }

    private static @Nullable PyType fromLhs(PyExpression lhs, @Nullable PyExpression rhs, TypeEvalContext ctx) {
        if (lhs instanceof PyParenthesizedExpression || rhs instanceof PyParenthesizedExpression) {
            // unwrap parentheses
            if (!(PyPsiUtilsCore.flattenParens(lhs) instanceof PyExpression lhsUnparenthesized)) {
                return null;
            }
            if (!(PyPsiUtilsCore.flattenParens(rhs) instanceof PyExpression rhsUnparenthesized)) {
                return null;
            }
            return fromLhs(lhsUnparenthesized, rhsUnparenthesized, ctx);
        }

        if (lhs instanceof PySequenceExpression sequenceExpression) {
            // try to mutually descent the nested sequences both on lhs and on rhs
            List<@Nullable PyType> tupleElementTypes = new ArrayList<>();
            PyExpression[] elements = sequenceExpression.getElements();
            for (int idx = 0; idx < elements.length; idx++) {
                PyExpression lhsElem = elements[idx];
                PyExpression rhsElem = null;
                if (rhs instanceof PySequenceExpression rhsSequence) {
                    PyExpression[] rhsElements = rhsSequence.getElements();
                    rhsElem = idx < rhsElements.length ? rhsElements[idx] : null;
                }
                PyType elemType = fromLhs(lhsElem, rhsElem, ctx);
                tupleElementTypes.add(elemType != null ? elemType : PyAnyType.getUnknown());
            }
            if (rhs instanceof PyTupleExpression) {
                // On the RHS of the assignment we are inside a tuple, hence it is safe to downcast the current type to tuple.
                // The benefit is that we can preserve the positional element type information.
                return PyTupleType.create(lhs, tupleElementTypes);
            }
            else {
                List<@Nullable PyType> iterableElementTypes = new ArrayList<>();
                for (PyType tupleElementType : tupleElementTypes) {
                    if (tupleElementType instanceof PyUnpackedTupleType unpackedTupleType) {
                        iterableElementTypes.addAll(unpackedTupleType.getElementTypes()); // simplify
                    }
                    else {
                        iterableElementTypes.add(tupleElementType);
                    }
                }
                if (iterableElementTypes.contains(PyAnyType.getUnknown())) {
                    return createIterableType(lhs, PyAnyType.getUnknown()); // simplify
                }
                if (iterableElementTypes.contains(PyAnyType.getAny())) {
                    return createIterableType(lhs, PyAnyType.getAny()); // simplify
                }
                PyType iterableElementTypesUnion = PyUnionType.unionOrUnknown(iterableElementTypes);
                return createIterableType(lhs, iterableElementTypesUnion);
            }
        }
        else if (lhs instanceof PyStarExpression starExpression) {
            PyExpression starChild = starExpression.getExpression();
            PyType starChildType = starChild == null ? null : fromLhs(starChild, rhs, ctx);
            if (starChildType instanceof PyTupleType tupleType) {
                if (tupleType.isHomogeneous()) {
                    return PyUnpackedTupleTypeImpl.createUnbound(tupleType.getIteratedItemType());
                }
                else {
                    return PyUnpackedTupleTypeImpl.create(tupleType.getElementTypes());
                }
            }
            return starChildType;
        }
        else if (lhs instanceof PySubscriptionExpression subscriptionExpression) {
            PyType operandType = ctx.getType(subscriptionExpression.getOperand());
            PyType iterableType = operandType instanceof PyClassType classType && classType.isParameterized()
                ? PyTypeChecker.getIteratedItemType(classType, ctx)
                : PyAnyType.getAny();

            if (subscriptionExpression.getIndexExpression() instanceof PySliceItem) {
                return createIterableType(lhs, iterableType);
            }
            return iterableType;
        }
        else if (lhs instanceof PyTargetExpression targetExpression) {
            // the following code is supposed to only consider explicitly declared type annotations
            PsiElement resolvedReference = targetExpression.getReference().resolve();

            if (resolvedReference instanceof PyNamedParameter namedParameter) {
                PsiElement parameterList = namedParameter.getParent();
                int indexOfExpr = parameterList instanceof PyParameterList list ? ArrayUtil.indexOf(list.getParameters(), namedParameter) : -1;
                PsiElement parameterListHolder = parameterList != null ? parameterList.getParent() : null;
                PyType callableType;
                if (parameterListHolder instanceof PyFunction function) {
                    callableType = ctx.getType(function);
                }
                else if (parameterListHolder instanceof PyLambdaExpression lambdaExpression) {
                    callableType = getExpectedType(lambdaExpression, ctx);
                }
                else {
                    callableType = null;
                }

                if (callableType instanceof PyCallableType pyCallableType) {
                    List<PyCallableParameter> parameters = pyCallableType.getParameters(ctx);
                    if (parameters != null && indexOfExpr >= 0 && indexOfExpr < parameters.size()) {
                        return parameters.get(indexOfExpr).getType(ctx);
                    }
                }
                return null;
            }
            if (resolvedReference instanceof PyTypedElement) {
                Ref<PyType> pyType = new PyTypingTypeProvider().getReferenceType(resolvedReference, ctx, null);
                if (pyType != null) {
                    return pyType.get();
                }
            }
            Ref<PyType> pyType = new PyTypingTypeProvider().getReferenceType(lhs, ctx, null);
            if (pyType != null) {
                return pyType.get();
            }
            // TODO: maybe support types from Doc-Strings using: (expr as PyTargetExpressionImpl).getTypeFromDocString()
            return null;
        }

        return null;
    }

    private static @Nullable PyType fromYield(PyExpression expr, TypeEvalContext ctx) {
        if (!(expr.getParent() instanceof PyYieldExpression parent)) {
            return null;
        }
        PyFunction funScope = PsiTreeUtil.getParentOfType(parent, PyFunction.class);
        if (funScope == null) {
            return null;
        }
        // Without a declaration, the return type is inferred from the yielded values, so it cannot constrain them
        if (funScope.getAnnotation() == null && funScope.getTypeCommentAnnotation() == null) {
            return null;
        }

        PyType returnType = ctx.getReturnType(funScope);
        PyTypingTypeProvider.GeneratorTypeDescriptor generatorDescriptor = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGenerator(returnType);
        PyType yieldType = generatorDescriptor != null && generatorDescriptor.yieldType() != null
            ? generatorDescriptor.yieldType()
            : PyAnyType.getUnknown();
        if (parent.isDelegating()) {
            return createIterableType(expr, yieldType);
        }
        return yieldType;
    }

    private static @Nullable PyType fromReturn(PyExpression expr, TypeEvalContext ctx) {
        if (!(expr.getParent() instanceof PyReturnStatement parent)) {
            return null;
        }
        PyFunction funScope = PsiTreeUtil.getParentOfType(parent, PyFunction.class);
        if (funScope == null) {
            return null;
        }
        if (funScope.getAnnotation() == null) {
            return null; // no explicit return type annotation, hence any return value is acceptable
        }

        PyType returnType = ctx.getReturnType(funScope);
        if (funScope.isAsync()) {
            Ref<PyType> unwrapped = PyTypingTypeProvider.unwrapCoroutineReturnType(returnType);
            return unwrapped != null ? unwrapped.get() : null;
        }
        PyTypingTypeProvider.GeneratorTypeDescriptor generatorDescriptor = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGenerator(returnType);
        PyType generatorReturnType = generatorDescriptor != null ? generatorDescriptor.returnType() : null;
        return generatorReturnType != null ? generatorReturnType : returnType;
    }

    /**
     * Returns the PyType of a tuple expression at the given index.
     * <p>
     * By design this method computes the complete array of the tuple elements.
     * The reason is that this makes it much easier to spot bugs while having only minimal impact on memory/time performance.
     */
    private static @Nullable PyType getElementTypeAtTupleIndex(PyTupleExpression tupleExpr, PyTupleType tupleType, int indexOfExpr) {
        if (indexOfExpr < 0) {
            return null;
        }

        PyType[] tupleTypeArray = new PyType[tupleExpr.getElements().length];
        List<PyType> elementTypes = tupleType.getElementTypes();
        int variadicRepeatCount = tupleExpr.getElements().length - elementTypes.size() + 1;
        int arrayIdx = 0;
        for (PyType elemType : Lists.getFirstItems(elementTypes, tupleTypeArray.length)) {
            if (elemType instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound()) {
                for (int i = 0; i < variadicRepeatCount; i++) {
                    tupleTypeArray[arrayIdx++] = ContainerUtil.getFirstItem(unpackedTupleType.getElementTypes());
                }
            }
            else if (elemType instanceof PyTupleType elemTupleType && elemTupleType.isHomogeneous()) {
                for (int i = 0; i < variadicRepeatCount; i++) {
                    tupleTypeArray[arrayIdx++] = ContainerUtil.getFirstItem(elemTupleType.getElementTypes());
                }
            }
            else {
                tupleTypeArray[arrayIdx++] = elemType;
            }
        }
        if (indexOfExpr < tupleTypeArray.length) {
            return tupleTypeArray[indexOfExpr];
        }
        return null;
    }

    /**
     * Computes the expected type of the operand {@code expr} inside the binary expression {@code binaryExpr}:
     * - <b>Rich comparison</b> ({@code <}, {@code >}, {@code <=}, {@code >=}, {@code ==}, {@code !=}, {@code <>}): synthetic dunder protocol (e.g., {@code __lt__}), unioned with {@code Any}
     * - <b>Membership</b> ({@code in}, {@code not in}): {@code __contains__} protocol for the container operand
     * - <b>Identity</b> ({@code is}, {@code is not}): no expectation, returns null
     * - <b>Short-circuit logical</b> ({@code and}, {@code or}): propagates the parent's expected type
     * - <b>Sequence repetition</b> ({@code seq * int} or {@code int * seq}): {@code SupportsIndex} ({@code __index__() -> int}) for the non-sequence operand
     * - <b>Arithmetic, bitwise, shift, matmul</b> ({@code +}, {@code -}, {@code *}, {@code /}, {@code //}, {@code %}, {@code **}, {@code @}, {@code <<}, {@code >>}, {@code &}, {@code |}, {@code ^}):
     * synthetic operator protocol (e.g., {@code __add__}/{@code __radd__}), unioned with {@code Any}
     * - <b>Fallback</b>: Otherwise propagate the parent's expected type.
     */
    private static @Nullable PyType fromBinaryExpression(PyExpression expr, PyBinaryExpression binaryExpr, TypeEvalContext ctx) {
        IElementType operator = binaryExpr.getOperator();
        if (operator == null) {
            return null;
        }

        PyType comparisonProtocolType = fromComparisonExpression(expr, binaryExpr, operator, ctx);
        if (comparisonProtocolType != null) {
            return comparisonProtocolType;
        }
        if (PyTokenTypes.COMPARISON_OPERATIONS.contains(operator)) {
            return null;
        }

        PyType expectedTypeOfWholeExpr = getExpectedType(binaryExpr, ctx);

        // Short-circuit operators: `a and b`, `a or b` evaluate to one of the operands.
        if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
            return expectedTypeOfWholeExpr;
        }

        if (operator == PyTokenTypes.MULT) {
            // Special case for sequence repetition: [1,2] * count.
            PyExpression otherOperand;
            if (expr == binaryExpr.getLeftExpression()) {
                otherOperand = binaryExpr.getRightExpression();
            }
            else if (expr == binaryExpr.getRightExpression()) {
                otherOperand = binaryExpr.getLeftExpression();
            }
            else {
                otherOperand = null;
            }
            if (otherOperand != null) {
                PyBuiltinCache cache = PyBuiltinCache.getInstance(expr);
                PyClassType intType = cache.getIntType();
                PyType typeOfOther = ctx.getType(otherOperand);
                if (isSequence(typeOfOther, ctx)) {
                    // Create:  `__index__() -> int`, i.e. `SupportsIndex`.
                    PyType syntheticType = createSyntheticDunderProtocolType(expr, "__index__", null, intType, ctx);
                    return syntheticType;
                }
            }
        }

        PyType operatorProtocolType = fromOperatorExpression(expr, binaryExpr, operator, ctx);
        if (operatorProtocolType != null) {
            return operatorProtocolType;
        }

        // Fallback: propagate the parent's expected type.
        return expectedTypeOfWholeExpr;
    }

    private static @Nullable String operandMethodName(PyExpression expr,
                                                      @Nullable PyExpression leftExpr,
                                                      @Nullable PyExpression rightExpr,
                                                      String directName,
                                                      String reflectedName) {
        return expr == leftExpr ? directName : expr == rightExpr ? reflectedName : null;
    }

    /**
     * Binary operator operands are constrained structurally by Python's operator protocol.
     * Since Python may let the other operand compensate via the direct/reflected fallback path,
     * the synthetic expectation is unioned with {@code Any}.
     */
    private static @Nullable PyType fromOperatorExpression(PyExpression expr,
                                                           PyBinaryExpression binaryExpr,
                                                           IElementType operator,
                                                           TypeEvalContext ctx) {
        PyExpression leftExpr = binaryExpr.getLeftExpression();
        PyExpression rightExpr = binaryExpr.getRightExpression();

        String methodName;
        if (operator == PyTokenTypes.PLUS) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__add__", "__radd__");
        }
        else if (operator == PyTokenTypes.MINUS) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__sub__", "__rsub__");
        }
        else if (operator == PyTokenTypes.MULT) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__mul__", "__rmul__");
        }
        else if (operator == PyTokenTypes.AT) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__matmul__", "__rmatmul__");
        }
        else if (operator == PyTokenTypes.DIV) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__truediv__", "__rtruediv__");
        }
        else if (operator == PyTokenTypes.FLOORDIV) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__floordiv__", "__rfloordiv__");
        }
        else if (operator == PyTokenTypes.PERC) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__mod__", "__rmod__");
        }
        else if (operator == PyTokenTypes.EXP) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__pow__", "__rpow__");
        }
        else if (operator == PyTokenTypes.LTLT) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__lshift__", "__rlshift__");
        }
        else if (operator == PyTokenTypes.GTGT) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__rshift__", "__rrshift__");
        }
        else if (operator == PyTokenTypes.AND) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__and__", "__rand__");
        }
        else if (operator == PyTokenTypes.OR) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__or__", "__ror__");
        }
        else if (operator == PyTokenTypes.XOR) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__xor__", "__rxor__");
        }
        else {
            methodName = null;
        }
        if (methodName == null) {
            return null;
        }

        // to keep systematic/self-explaining implementation below
        PyExpression otherOperand;
        if (expr == leftExpr) {
            otherOperand = rightExpr;
        }
        else if (expr == rightExpr) {
            otherOperand = leftExpr;
        }
        else {
            otherOperand = null;
        }
        if (otherOperand == null) {
            return null;
        }

        PyType otherOperandType = ctx.getType(otherOperand);
        PyType expectedType = getExpectedType(binaryExpr, ctx);
        PyType expectedResultType = expectedType instanceof PyAnyType || containsSyntheticProtocol(expectedType) || expectedType == null
            ? PyAnyType.getAny()
            : expectedType;
        PyType syntheticType = createSyntheticDunderProtocolType(expr, methodName, Ref.create(otherOperandType), expectedResultType, ctx);

        return PyUnionType.unionOrUnknown(syntheticType, PyAnyType.getAny());
    }

    /**
     * A synthetic protocol from an enclosing binary expression must not become the return type of this one:
     * it only exists in its own code fragment, so rendering it as text would either not resolve or resolve to the
     * protocol being declared, yielding a recursive protocol ({@code def __add__(...) -> _Supports__add__ | Any}).
     * This might happen in this example: {@code expr = (1,) + (True, 'spam') + ()}.
     */
    private static boolean containsSyntheticProtocol(@Nullable PyType type) {
        if (type instanceof PyUnionType unionType) {
            return ContainerUtil.exists(unionType.getMembers(), PyExpectedTypeJudgement::containsSyntheticProtocol);
        }
        else if (type instanceof PyClassType classType) {
            return Boolean.TRUE.equals(classType.getPyClass().getUserData(SYNTHETIC_PROTOCOL));
        }
        else {
            return false;
        }
    }

    private static @Nullable PyType fromComparisonExpression(PyExpression expr,
                                                             PyBinaryExpression binaryExpr,
                                                             IElementType operator,
                                                             TypeEvalContext ctx) {
        PyExpression leftExpr = binaryExpr.getLeftExpression();
        PyExpression rightExpr = binaryExpr.getRightExpression();

        String methodName;
        if (operator == PyTokenTypes.LT) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__lt__", "__gt__");
        }
        else if (operator == PyTokenTypes.GT) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__gt__", "__lt__");
        }
        else if (operator == PyTokenTypes.LE) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__le__", "__ge__");
        }
        else if (operator == PyTokenTypes.GE) {
            methodName = operandMethodName(expr, leftExpr, rightExpr, "__ge__", "__le__");
        }
        else if (operator == PyTokenTypes.EQEQ) {
            methodName = expr == leftExpr || expr == rightExpr ? "__eq__" : null;
        }
        else if (operator == PyTokenTypes.NE || operator == PyTokenTypes.NE_OLD) {
            methodName = expr == leftExpr || expr == rightExpr ? "__ne__" : null;
        }
        // `x in y` / `x not in y`: the container side is constrained.
        else if (operator == PyTokenTypes.IN_KEYWORD || operator == PyTokenTypes.NOT_KEYWORD) {
            methodName = expr == rightExpr ? "__contains__" : null;
        }
        // `is` / `is not` does not rely on a user-defined API.
        else if (operator == PyTokenTypes.IS_KEYWORD) {
            methodName = null;
        }
        else {
            methodName = null;
        }
        if (methodName == null) {
            return null;
        }

        PyType argumentType;
        if (operator == PyTokenTypes.IN_KEYWORD || operator == PyTokenTypes.NOT_KEYWORD) {
            argumentType = ctx.getType(leftExpr);
        }
        else if (expr == leftExpr && rightExpr != null) {
            argumentType = ctx.getType(rightExpr);
        }
        else if (expr == rightExpr) {
            argumentType = ctx.getType(leftExpr);
        }
        else {
            argumentType = null;
        }

        PyType boolType = PyBuiltinCache.getInstance(expr).getBoolType();
        if (boolType == null) {
            boolType = PyAnyType.getAny();
        }
        PyType syntheticType = createSyntheticDunderProtocolType(expr, methodName, Ref.create(argumentType), boolType, ctx);

        if (operator == PyTokenTypes.IN_KEYWORD || operator == PyTokenTypes.NOT_KEYWORD) {
            return syntheticType;
        }
        return PyUnionType.unionOrUnknown(syntheticType, PyAnyType.getAny());
    }

    // TODO: Either refactor this so that [PyStructuralType] is enhanced and used here. Or refactor its callers so that we do not need
    //       to create a synthetic protocol types at all, but instead use an naive approach to compute an expected type.
    private static PyType createSyntheticDunderProtocolType(PsiElement anchor,
                                                            String methodName,
                                                            @Nullable Ref<@Nullable PyType> argumentType,
                                                            @Nullable PyType returnType,
                                                            TypeEvalContext ctx) {
        String argumentTypeText = renderTypeHint(argumentType != null ? argumentType.get() : null, ctx);
        String argumentText = argumentType == null ? "" : ", other: " + argumentTypeText + ", /";
        String returnTypeText = renderTypeHint(returnType, ctx);
        String text = "import typing\n" +
            "class _Supports" + methodName + "(typing.Protocol):\n" +
            "  def " + methodName + "(self" + argumentText + ") -> " + returnTypeText + ": ...";

        PyExpressionCodeFragmentImpl codeFragment = new PyExpressionCodeFragmentImpl(anchor.getProject(), "dummy.py", text, false);
        codeFragment.setContext(anchor.getContainingFile());

        PyClass cls = PsiTreeUtil.findChildOfType(codeFragment, PyClass.class);
        if (cls == null) {
            throw new IllegalStateException("Failed to create synthetic protocol class from text:\n" + text);
        }
        cls.putUserData(SYNTHETIC_PROTOCOL, true);

        return new PyClassTypeImpl(cls, false);
    }

    private static boolean isSequence(@Nullable PyType type, TypeEvalContext ctx) {
        return type != null && PyABCUtil.isSubtype(type, PyNames.SEQUENCE, ctx);
    }

    private static String renderTypeHint(@Nullable PyType type, TypeEvalContext ctx) {
        if (type == null) {
            return "Any";
        }
        return PythonDocumentationProvider.getTypeHint(type, ctx);
    }

    private static @Nullable PyClassTypeImpl createIterableType(PsiElement anchor, @Nullable PyType elementType) {
        return PyCollectionTypeImpl.createTypeByQName(anchor, "typing." + PyNames.ITERABLE, false, Collections.singletonList(elementType));
    }
}
