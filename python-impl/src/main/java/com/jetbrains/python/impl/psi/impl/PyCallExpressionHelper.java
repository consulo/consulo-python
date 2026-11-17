// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.util.lang.ref.SoftReference;
import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstBinaryExpression;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters.Counter;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference.OperatorSide;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.impl.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyCompositeType;
import com.jetbrains.python.impl.psi.types.PyConcatenateType;
import com.jetbrains.python.impl.psi.types.PyIntersectionType;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PyOverloadType;
import com.jetbrains.python.impl.psi.types.PyParamSpecType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeChecker.GenericSubstitutions;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtil.FunctionBindingResult;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyQualifiedElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.ThreeState;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAny;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 */
public final class PyCallExpressionHelper {
    private PyCallExpressionHelper() {
    }

    /**
     * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
     *
     * @param expression the possible call, generally a result of chasing a chain of assignments
     * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
     */
    public static @Nullable Pair<String, PyFunction> interpretAsModifierWrappingCall(PyCallExpression expression) {
        PyExpression redefiningCallee = expression.getCallee();
        if (!expression.isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD)) {
            return null;
        }
        if (!(redefiningCallee instanceof PyReferenceExpression referenceExpr)) {
            return null;
        }
        String refName = referenceExpr.getReferencedName();
        if (!(PyNames.CLASSMETHOD.equals(refName) || PyNames.STATICMETHOD.equals(refName)) || !PyBuiltinCache.isInBuiltins(referenceExpr)) {
            return null;
        }
        // yes, really a case of "foo = classmethod(foo)"
        var argumentList = expression.getArgumentList();
        if (argumentList == null) {
            return null;
        }
        // really can't be any other way
        PyExpression[] arguments = argumentList.getArguments();
        if (arguments.length == 0 || !(arguments[0] instanceof PyReferenceExpression possibleOriginalRef)) {
            return null;
        }
        if (!(possibleOriginalRef.getReference().resolve() instanceof PyFunction original)) {
            return null;
        }
        // pinned down the original; replace our resolved callee with it and add flags.
        return Pair.create(refName, original);
    }

    public static @Nullable PyClass resolveCalleeClass(PyCallExpression expression) {
        PyExpression callee = expression.getCallee();

        QualifiedResolveResult resolveResult;
        PsiElement resolved;
        if (callee instanceof PyReferenceExpression referenceExpression) {
            // dereference
            resolveResult = referenceExpression.followAssignmentsChain(
                PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(callee.getProject())));
            resolved = resolveResult.getElement();
        }
        else {
            resolved = callee;
        }
        // analyze
        if (resolved instanceof PyClass pyClass) {
            return pyClass;
        }
        else if (resolved instanceof PyFunction function) {
            return function.getContainingClass();
        }

        return null;
    }

    /**
     * If the callee type is {@link PyClassType}, returns a callable type that is either a class
     * constructor or a {@code __call__} method. Otherwise, returns the callee type as is.
     */
    public static @Nullable PyType getCalleeType(PyExpression callee, PyResolveContext resolveContext) {
        return PyUtil.getNullableParameterizedCachedValue(callee, resolveContext, it -> {
            List<@Nullable PyType> types = new ArrayList<>();
            types.add(getExplicitCalleeType(callee, it));
            PyType implicitCalleeType = getImplicitCalleeType(callee, it);
            if (implicitCalleeType != null) {
                types.add(implicitCalleeType);
            }
            return PyUnionType.unionOrUnknown(types);
        });
    }

    /**
     * @deprecated Use {@code getCalleeType(PyExpression, PyResolveContext)}
     */
    @Deprecated
    public static List<PyCallableType> multiResolveCallee(PyCallExpression callExpression, PyResolveContext resolveContext) {
        PyExpression callee = callExpression.getCallee();
        PyType calleeType = callee != null ? getCalleeType(callee, resolveContext) : null;
        if (calleeType == null) {
            calleeType = PyAnyType.getUnknown();
        }
        return flattenToCallables(calleeType);
    }

    private static List<PyCallableType> flattenToCallables(@Nullable PyType type) {
        if (type instanceof PyCompositeType compositeType) {
            List<PyCallableType> result = new ArrayList<>();
            for (PyType member : compositeType.getMembers()) {
                result.addAll(flattenToCallables(member));
            }
            return result;
        }
        else {
            return PyTypeUtil.getCallableItems(type);
        }
    }

    public static List<ResolvedOperator> multiResolveOperator(PyQualifiedElement operatorOwner,
                                                              PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();

        List<ResolvedOperator> result = new ArrayList<>();
        for (PyOperatorReference.OperatorResolveResult it : new PyOperatorReference(operatorOwner, resolveContext).resolveOperator()) {
            PyType selfType = it.selfType();
            List<? extends RatedResolveResult> resolveResults = it.resolveResults();
            PyType boundType;
            if (selfType instanceof PyClassType classType) {
                boundType = PyTypeUtil.getTypeOfBoundMember(classType, resolveResults, context);
            }
            else {
                // Workaround for `CythonBuiltinType`. Since it doesn't implement `PyInstantiableType`,
                // methods cannot be bound to it. Just drop the first function parameter.
                // See `CythonTypeCheckerInspectionTest.testTypeCheckInCython`
                PyType type = PyTypeUtil.getTypeOfMember(resolveResults, context);
                boundType = PyTypeUtil.mapCallableType(type, subType -> {
                    List<PyCallableParameter> parameters = subType.getParameters(context);
                    if (parameters == null) {
                        return subType;
                    }
                    return new PyCallableTypeImpl(
                        ParamHelper.dropSelf(parameters),
                        subType.getReturnType(context),
                        subType.getCallable(),
                        subType.getModifier()
                    );
                });
            }
            result.add(new ResolvedOperator(selfType, boundType, it.arguments(), it.side()));
        }
        return result;
    }

    private static @Nullable PyType getExplicitCalleeType(PyExpression callee, PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        return PyTypeUtil.compositeMap(context.getType(callee), type -> {
            Ref<@Nullable PyType> provided = PyTypeProvider.EP_NAME.computeSafeIfAny(it -> it.prepareCalleeTypeForCall(type, callee, context));
            if (provided != null) {
                PyType providedType = provided.get();
                return providedType != null ? providedType : PyAnyType.getUnknown();
            }
            // When invoking cls(), turn type[Self] into Self.
            // Otherwise, we will delegate to __init__() of its scope class and return a concrete type class
            // as a call result, losing Self.
            // See e.g. PyTypeAliasAndFormsTest.SelfTypeCheckerInspections.`Self in class methods`
            else if (type instanceof PySelfType selfType) {
                if (selfType.isDefinition() && constructsAnotherType(selfType.getScopeClassType(), resolveContext)) {
                    return createCallableFromClass(selfType.getScopeClassType(), resolveContext);
                }
                else {
                    return selfType;
                }
            }
            else if (type instanceof PyClassType classType) {
                return createCallableFromClass(classType, resolveContext);
            }
            else {
                return type;
            }
        });
    }

    private static @Nullable PyType getImplicitCalleeType(PyExpression callee, PyResolveContext resolveContext) {
        if (!resolveContext.allowImplicits()) {
            return null;
        }

        TypeEvalContext context = resolveContext.getTypeEvalContext();
        if (callee instanceof PyQualifiedExpression qualifiedExpression) {
            String referencedName = qualifiedExpression.getReferencedName();
            if (referencedName == null) {
                return null;
            }

            PyExpression qualifier = qualifiedExpression.getQualifier();
            if (qualifier == null || !canQualifyAnImplicitName(qualifier)) {
                return null;
            }

            PyType qualifierType = context.getType(qualifier);
            if (PyTypeChecker.isUnknown(qualifierType, context) ||
                qualifierType instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages()
            ) {
                ResolveResultList resolveResults = new ResolveResultList();
                PyResolveUtil.addImplicitResolveResults(referencedName, resolveResults, qualifiedExpression);

                List<PyCallableType> callables = new ArrayList<>();
                for (PsiElement element : PyUtil.filterTopPriorityElements(
                    forEveryScopeTakeOverloadsOtherwiseImplementations(resolveResults, context, ResolveResult::getElement))) {
                    if (element instanceof PyTypedElement typedElement) {
                        PyTypeUtil.toStream(context.getType(typedElement)).forEach(type -> {
                            if (type instanceof PyCallableType callableType) {
                                callables.add(callableType);
                            }
                        });
                    }
                }
                return callables.isEmpty() ? null : PyUnionType.unionOrUnknown(new ArrayList<>(callables));
            }
        }

        return null;
    }

    /**
     * Calls the {@link #getImplicitArgumentCount} (full version)
     * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
     *
     * @param expression the call site, where arguments are given.
     * @param function   resolved method which is being called; plain functions are OK but make little sense.
     * @return a non-negative number of parameters that are implicit to this call.
     */
    public static int getImplicitArgumentCount(PyExpression expression, PyFunction function, PyResolveContext resolveContext) {
        PyType type = resolveContext.getTypeEvalContext().getType(expression);
        boolean isConstructorCall = type instanceof PyClassType classType && classType.isDefinition();
        if (isConstructorCall) {
            return getImplicitArgumentCount(function, function.getModifier(), true, false, false);
        }
        if (!(expression instanceof PyReferenceExpression referenceExpression)) {
            return 0;
        }
        QualifiedResolveResult followed = referenceExpression.followAssignmentsChain(resolveContext);
        List<PyExpression> qualifiers = followed.getQualifiers();
        PyExpression firstQualifier = qualifiers.isEmpty() ? null : qualifiers.get(0);
        boolean isByInstance = isQualifiedByInstance(function, qualifiers, resolveContext.getTypeEvalContext());
        boolean isByClass = firstQualifier != null && isQualifiedByClass(function, firstQualifier, resolveContext.getTypeEvalContext());
        return getImplicitArgumentCount(function, function.getModifier(), false, isByInstance, isByClass);
    }

    /**
     * Finds how many arguments are implicit in a given call.
     *
     * @param callable     resolved method which is being called; non-methods immediately return 0.
     * @param isByInstance true if the call is known to be by instance (not by class).
     * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
     * because one parameter ('self') is implicit.
     */
    private static int getImplicitArgumentCount(PyCallable callable,
                                                PyAstFunction.@Nullable Modifier modifier,
                                                boolean isConstructorCall,
                                                boolean isByInstance,
                                                boolean isByClass) {
        int implicitOffset = 0;
        boolean firstIsArgsOrKwargs = false;
        PyParameter[] parameters = callable.getParameterList().getParameters();
        if (parameters.length > 0) {
            PyParameter first = parameters[0];
            PyNamedParameter named = first.getAsNamed();
            if (named != null && (named.isPositionalContainer() || named.isKeywordContainer())) {
                firstIsArgsOrKwargs = true;
            }
        }
        if (!firstIsArgsOrKwargs && (isByInstance || isConstructorCall)) {
            implicitOffset += 1;
        }
        PyFunction method = callable.asMethod();
        if (method == null) {
            return implicitOffset;
        }

        if (PyUtil.isNewMethod(method)) {
            return isConstructorCall ? 1 : 0;
        }
        if (!isByInstance && !isByClass && PyUtil.isInitMethod(method)) {
            return 1;
        }

        // decorators?
        if (modifier == PyAstFunction.Modifier.STATICMETHOD) {
            if (isByInstance && implicitOffset > 0) {
                implicitOffset -= 1; // might have marked it as implicit 'self'
            }
        }
        else if (modifier == PyAstFunction.Modifier.CLASSMETHOD) {
            if (!isByInstance) {
                implicitOffset += 1; // Both Foo.method() and foo.method() have implicit the first arg
            }
        }
        return implicitOffset;
    }

    private static boolean isQualifiedByInstance(@Nullable PyCallable callable,
                                                 List<? extends @Nullable PyExpression> qualifiers,
                                                 TypeEvalContext context) {
        PyDocStringOwner owner = PsiTreeUtil.getStubOrPsiParentOfType(callable, PyDocStringOwner.class);
        if (!(owner instanceof PyClass)) {
            return false;
        }
        // true = call by instance
        if (qualifiers.isEmpty()) {
            return true; // unqualified + method = implicit constructor call
        }
        for (PyExpression qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            ThreeState byInstance = isQualifiedByInstance(callable, qualifier, context);
            if (byInstance != ThreeState.UNSURE) {
                return byInstance.toBoolean();
            }
        }
        return true;
    }

    private static ThreeState isQualifiedByInstance(@Nullable PyCallable callable, PyExpression qualifier, TypeEvalContext context) {
        if (isQualifiedByClass(callable, qualifier, context)) {
            return ThreeState.NO;
        }
        PyType qualifierType = context.getType(qualifier);
        // TODO: handle UnionType
        if (qualifierType instanceof PyModuleType) {
            return ThreeState.UNSURE;
        }
        return ThreeState.YES; // NOTE. best guess: unknown qualifier is more probably an instance.
    }

    private static boolean isQualifiedByClass(@Nullable PyCallable callable, PyExpression qualifier, TypeEvalContext context) {
        PyType qualifierType = context.getType(qualifier);

        if (qualifierType instanceof PyClassType classType) {
            return classType.isDefinition() && belongsToSpecifiedClassHierarchy(callable, classType.getPyClass(), context);
        }
        else if (qualifierType instanceof PyClassLikeType classLikeType) {
            return classLikeType.isDefinition(); // Any definition means callable is classmethod
        }
        else if (qualifierType instanceof PyUnionType unionType) {
            var members = unionType.getMembers();

            if (members.stream().allMatch(it -> it == null || isNoneType(it) || it instanceof PyClassType)) {
                return members.stream()
                    .filter(it -> it instanceof PyClassType)
                    .map(it -> (PyClassType) it)
                    .filter(it -> belongsToSpecifiedClassHierarchy(callable, it.getPyClass(), context))
                    .allMatch(PyClassType::isDefinition);
            }
        }

        return false;
    }

    private static boolean belongsToSpecifiedClassHierarchy(@Nullable PsiElement element, PyClass cls, TypeEvalContext context) {
        PyClass parent = PsiTreeUtil.getStubOrPsiParentOfType(element, PyClass.class);
        return parent != null && (cls.isSubclass(parent, context) || parent.isSubclass(cls, context));
    }

    /**
     * This method should not be called directly,
     * please obtain its result via {@link TypeEvalContext#getType} with {@code call} as an argument.
     */
    public static @Nullable PyType getCallType(PyCallExpression expression,
                                               TypeEvalContext context,
                                               @SuppressWarnings("unused") TypeEvalContext.Key key) {
        PyExpression callee = expression.getCallee();
        if (callee instanceof PyReferenceExpression) {
            // hardwired special cases
            if (PyNames.SUPER.equals(callee.getText())) {
                Maybe<@Nullable PyType> superCallType = getSuperCallType(expression, context);
                if (superCallType.isDefined()) {
                    return superCallType.value();
                }
            }
            if ("type".equals(callee.getText())) {
                PyExpression[] args = expression.getArguments();
                if (args.length == 1) {
                    PyExpression arg = args[0];
                    PyType argType = context.getType(arg);
                    if (argType instanceof PyClassType classType) {
                        if (!classType.isDefinition()) {
                            return classType instanceof PySelfType selfType ? selfType.toClass() : context.getType(classType.getPyClass());
                        }
                    }
                    else {
                        return PyAnyType.getUnknown();
                    }
                }
            }
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        PyType calleeType = callee != null ? getCalleeType(callee, resolveContext) : null;
        if (calleeType == null) {
            calleeType = PyAnyType.getUnknown();
        }
        return call(calleeType, expression, toCallableArguments(Arrays.asList(expression.getArguments())), context).getType();
    }

    /**
     * This method should not be called directly,
     * please obtain its result via {@link TypeEvalContext#getType} with {@code subscription} as an argument.
     */
    public static @Nullable PyType getCallType(PySubscriptionExpression expression,
                                               TypeEvalContext context,
                                               @SuppressWarnings("unused") TypeEvalContext.Key key) {
        return getCallType(resolveOperatorCalls(expression, context));
    }

    private static @Nullable PyType getCallType(List<OperatorCall> calls) {
        List<@Nullable PyType> types = new ArrayList<>(calls.size());
        for (OperatorCall call : calls) {
            types.add(call.getType());
        }
        return PyUnionType.unionOrUnknown(types);
    }

    /**
     * CPython's runtime rule "prefer rhs.__r&lt;op&gt;__ if rhs is a strict subclass of lhs" is not reproduced,
     * as it relies on runtime classes rather than annotations, making annotation-based approximation unsound.
     *
     * @see <a href="https://github.com/astral-sh/ty/issues/1154">#1154</a>
     * @see <a href="https://github.com/astral-sh/ty/issues/630">#630</a>
     */
    public static @Nullable PyType getCallType(PyBinaryExpression expression,
                                               TypeEvalContext context,
                                               @SuppressWarnings("unused") TypeEvalContext.Key key) {
        if (!(PyAstBinaryExpression.getChainedComparisonAwareLeftExpression(expression) instanceof PyExpression leftExpr)) {
            return PyAnyType.getUnknown();
        }
        PyExpression rightExpr = expression.getRightExpression();
        if (rightExpr == null) {
            return PyAnyType.getUnknown();
        }

        List<OperatorCall> operators = resolveOperatorCalls(expression, context);
        List<OperatorCall> matchingOperators = filter(operators, it -> it.matched);

        PyType leftType = context.getType(leftExpr);
        PyType rightType = context.getType(rightExpr);

        boolean isTypeUnionSyntax = expression.isOperator("|") &&
            (leftType instanceof PyClassType leftClassType && leftClassType.isDefinition() ||
                rightType instanceof PyClassType rightClassType && rightClassType.isDefinition());

        if (PyTypingTypeProvider.isInsideTypeHint(expression, context) || isTypeUnionSyntax) {
            return getCallType(matchingOperators.isEmpty() ? operators : matchingOperators);
        }

        List<OperatorCall> normalOperators = filter(matchingOperators, it -> it.side != OperatorSide.RIGHT);

        if (!normalOperators.isEmpty() && areAllTypesCoveredByCandidates(leftExpr, normalOperators, context)) {
            return getCallType(normalOperators);
        }
        else {
            return getCallType(matchingOperators.isEmpty() ? operators : matchingOperators);
        }
    }

    public static @Nullable PyType getCallType(PyAugAssignmentStatement statement,
                                               TypeEvalContext context,
                                               @SuppressWarnings("unused") TypeEvalContext.Key key) {
        List<OperatorCall> operators = resolveOperatorCalls(statement, context);

        List<OperatorCall> matchingOperators = filter(operators, it -> it.matched);

        List<OperatorCall> inplaceOperators = filter(matchingOperators, it -> it.side == OperatorSide.INPLACE);
        if (!inplaceOperators.isEmpty() && areAllTypesCoveredByCandidates(statement.getTarget(), inplaceOperators, context)) {
            return getCallType(inplaceOperators);
        }

        List<OperatorCall> normalOperators = filter(matchingOperators, it -> it.side == OperatorSide.LEFT);
        if (!normalOperators.isEmpty() && areAllTypesCoveredByCandidates(statement.getTarget(), normalOperators, context)) {
            return getCallType(normalOperators);
        }

        Set<OperatorCall> leftOperatorsSet = new LinkedHashSet<>(inplaceOperators);
        leftOperatorsSet.addAll(normalOperators);
        List<OperatorCall> leftOperators = new ArrayList<>(leftOperatorsSet);
        if (!leftOperators.isEmpty() && areAllTypesCoveredByCandidates(statement.getTarget(), leftOperators, context)) {
            return getCallType(leftOperators);
        }

        return getCallType(matchingOperators.isEmpty() ? operators : matchingOperators);
    }

    private static boolean areAllTypesCoveredByCandidates(PyExpression operandExpression,
                                                          List<OperatorCall> candidates,
                                                          TypeEvalContext context) {
        return PyTypeUtil.toStream(context.getType(operandExpression)).allMatch(member ->
            member instanceof PyClassType && candidates.stream().anyMatch(it -> Objects.equals(it.selfType, member))
        );
    }

    private static List<OperatorCall> resolveOperatorCalls(PyQualifiedElement operator, TypeEvalContext context) {
        List<OperatorCall> result = new ArrayList<>();
        for (ResolvedOperator op : multiResolveOperator(operator, PyResolveContext.defaultContext(context))) {
            List<PyCallableArgument> arguments = toCallableArguments(op.arguments());
            CallResult call = call(op.method(), operator, arguments, context);
            result.add(new OperatorCall(op.selfType(), op.side(), call.matched, call.getType));
        }
        return result;
    }

    private static final class OperatorCall {
        final PyType selfType;
        final OperatorSide side;
        final boolean matched;
        final Supplier<@Nullable PyType> getType;

        OperatorCall(PyType selfType, OperatorSide side, boolean matched, Supplier<@Nullable PyType> getType) {
            this.selfType = selfType;
            this.side = side;
            this.matched = matched;
            this.getType = getType;
        }

        @Nullable PyType getType() {
            return getType.get();
        }
    }

    public static @Nullable Ref<@Nullable PyType> getSpecialMethodCallType(@Nullable PyType objectType,
                                                                           String methodName,
                                                                           List<PyCallableArgument> arguments,
                                                                           TypeEvalContext context) {
        return getSpecialMethodCallType(objectType, methodName, arguments, context, false);
    }

    public static @Nullable Ref<@Nullable PyType> getSpecialMethodCallType(@Nullable PyType objectType,
                                                                           String methodName,
                                                                           List<PyCallableArgument> arguments,
                                                                           TypeEvalContext context,
                                                                           boolean onlyMatched) {
        return getSpecialMethodCallType(objectType, methodName, arguments, context, onlyMatched, false);
    }

    public static @Nullable Ref<@Nullable PyType> getSpecialMethodCallType(@Nullable PyType objectType,
                                                                           String methodName,
                                                                           List<PyCallableArgument> arguments,
                                                                           TypeEvalContext context,
                                                                           boolean onlyMatched,
                                                                           boolean skipObject) {
        if (!(objectType instanceof PyClassLikeType objectClassLikeType)) {
            return null;
        }
        PyClassLikeType classType;
        if (objectClassLikeType.isDefinition()) {
            classType = objectClassLikeType.getMetaClassType(context, true);
            if (classType == null) {
                return null;
            }
        }
        else {
            // An implicitly invoked special method is looked up on the type of the object. `resolveMember`
            // searches both the object and its type (the metaclass when `PyClassLikeType.isDefinition` is true).
            // To exclude metaclass attributes from the resolve result, we pass the instance and accept that
            // instance attributes may shadow the class ones.
            classType = objectClassLikeType;
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<? extends RatedResolveResult> resolveResults = classType.resolveMember(methodName, null, AccessDirection.READ, resolveContext);
        if (resolveResults == null || resolveResults.isEmpty()) {
            return null;
        }
        // TODO re-use resolveMemberSkippingClass
        if (skipObject) {
            PyClass containingClass = PyTypeUtil.getContainingClass(resolveResults);
            if (containingClass != null && PyNames.FQN.OBJECT.equals(containingClass.getQualifiedName())) {
                return null;
            }
        }
        PyType memberType = PyTypeUtil.getTypeOfBoundMember(classType, resolveResults, context, null, objectClassLikeType);
        CallResult call = call(memberType, null, arguments, context);
        return onlyMatched && !call.matched ? null : Ref.create(call.getType());
    }

    public static @Nullable PyType getCallType(@Nullable PyType type, List<PyCallableArgument> arguments, TypeEvalContext context) {
        return call(type, null, arguments, context).getType();
    }

    private static CallResult call(@Nullable PyType type,
                                   @Nullable PyElement callSite,
                                   List<PyCallableArgument> arguments,
                                   TypeEvalContext context) {
        if (type instanceof PyIntersectionType intersectionType) {
            List<CallResult> members = new ArrayList<>();
            for (PyType member : intersectionType.getMembers()) {
                members.add(call(member, callSite, arguments, context));
            }
            List<CallResult> matchingMembers = filter(members, it -> it.matched);
            return new CallResult(!matchingMembers.isEmpty(), () -> {
                List<@Nullable PyType> types = new ArrayList<>();
                for (CallResult it : matchingMembers.isEmpty() ? members : matchingMembers) {
                    types.add(it.getType());
                }
                return PyIntersectionType.intersectionOrTop(types);
            });
        }
        else if (type instanceof PyUnsafeUnionType unsafeUnionType) {
            List<CallResult> members = new ArrayList<>();
            for (PyType member : unsafeUnionType.getMembers()) {
                members.add(call(member, callSite, arguments, context));
            }
            return new CallResult(members.stream().anyMatch(it -> it.matched), () -> {
                List<@Nullable PyType> types = new ArrayList<>();
                for (CallResult it : members) {
                    types.add(it.getType());
                }
                return PyUnsafeUnionType.unsafeUnion(types);
            });
        }
        else if (type instanceof PyUnionType unionType) {
            List<CallResult> members = new ArrayList<>();
            for (PyType member : unionType.getMembers()) {
                members.add(call(member, callSite, arguments, context));
            }
            return new CallResult(members.stream().allMatch(it -> it.matched), () -> {
                List<@Nullable PyType> types = new ArrayList<>();
                for (CallResult it : members) {
                    types.add(it.getType());
                }
                return PyUnionType.unionOrUnknown(types);
            });
        }
        else if (type instanceof PyOverloadType || type instanceof PyCallableType) {
            List<PyCallableType> overloads = PyTypeUtil.getCallableItems(type);
            // An overloaded function whose overloads have no callable type yet, e.g. while a type engine starts
            if (overloads.isEmpty()) {
                return new CallResult(false, PyAnyType::getUnknown);
            }
            List<PyCallableType> matchingOverloads = selectMatchingOverloads(overloads, arguments, context);
            return new CallResult(!matchingOverloads.isEmpty(), () -> {
                List<@Nullable PyType> types = new ArrayList<>();
                for (PyCallableType it : matchingOverloads.isEmpty() ? overloads : matchingOverloads) {
                    types.add(PyNeverType.convertNoReturnToNever(it.getCallType(context, callSite, arguments)));
                }
                return PyUnsafeUnionType.unsafeUnion(types);
            });
        }
        else {
            return new CallResult(false, PyAnyType::getUnknown);
        }
    }

    private static final class CallResult {
        final boolean matched;
        final Supplier<@Nullable PyType> getType;

        CallResult(boolean matched, Supplier<@Nullable PyType> getType) {
            this.matched = matched;
            this.getType = getType;
        }

        @Nullable PyType getType() {
            return getType.get();
        }
    }

    public static List<PyCallableType> selectMatchingOverloads(List<PyCallableType> overloads,
                                                               List<PyCallableArgument> arguments,
                                                               TypeEvalContext context) {
        List<PyCallableType> matchingOverloads = filter(overloads, it -> matchesByArgumentTypes(it, arguments, context));
        if (matchingOverloads.size() <= 1) {
            return matchingOverloads;
        }
        boolean someArgumentsHaveUnknownType = arguments.stream().anyMatch(it -> isAnyOrUnknown(it.getType(context)));
        return someArgumentsHaveUnknownType ? matchingOverloads : List.of(matchingOverloads.get(0));
    }

    private static Maybe<@Nullable PyType> getSuperCallType(PyCallExpression expression, TypeEvalContext context) {
        PyExpression callee = expression.getCallee();
        if (!(callee instanceof PyReferenceExpression referenceCallee)) {
            return new Maybe<>();
        }
        PsiElement mustBeSuper = referenceCallee.getReference().resolve();
        if (mustBeSuper != PyBuiltinCache.getInstance(expression).getClass(PyNames.SUPER)) {
            return new Maybe<>();
        }
        var argumentList = expression.getArgumentList();
        if (argumentList == null) {
            return new Maybe<>();
        }
        PyExpression[] args = argumentList.getArguments();
        PyClass containingClass = PsiTreeUtil.getParentOfType(expression, PyClass.class);
        if (containingClass != null && args.length > 1) {
            PyExpression firstArg = args[0];
            if (!(firstArg instanceof PyReferenceExpression firstArgRef)) {
                return new Maybe<>();
            }
            PyExpression qualifier = firstArgRef.getQualifier();
            if (qualifier != null && PyNames.__CLASS__.equals(firstArgRef.getReferencedName())) {
                var qualifierReference = qualifier.getReference();
                PsiElement element = qualifierReference != null ? qualifierReference.resolve() : null;
                if (element instanceof PyParameter) {
                    PyParameterList parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
                    if (parameterList != null && element == parameterList.getParameters()[0]) {
                        return new Maybe<>(getSuperCallTypeForArguments(context, containingClass, args[1]));
                    }
                }
            }
            PsiElement possibleClass = firstArgRef.getReference().resolve();
            if (possibleClass instanceof PyClass pyClass && pyClass.isNewStyleClass(context)) {
                return new Maybe<>(getSuperCallTypeForArguments(context, pyClass, args[1]));
            }
            if (possibleClass instanceof PyNamedParameter namedParameter) {
                PyType paramType = context.getType(namedParameter);
                if (paramType instanceof PyClassType classType) {
                    return new Maybe<>(getSuperCallTypeForArguments(context, classType.getPyClass(), args[1]));
                }
            }
        }
        else if (expression.getContainingFile() instanceof PyFile pyFile && pyFile.getLanguageLevel().isPy3K() && containingClass != null) {
            return new Maybe<>(getSuperClassUnionType(containingClass, context));
        }
        return new Maybe<>();
    }

    private static @Nullable PyType getSuperCallTypeForArguments(TypeEvalContext context, PyClass firstClass, @Nullable PyExpression secondArg) {
        // check 2nd argument, too; it should be an instance
        if (secondArg != null) {
            PyType secondType = context.getType(secondArg);
            if (secondType instanceof PyClassType secondClassType) {
                // imitate isinstance(secondArg, possibleClass)
                PyClass secondClass = secondClassType.getPyClass();
                if (CompletionUtilCore.getOriginalOrSelf(firstClass) == secondClass) {
                    return getSuperClassUnionType(firstClass, context);
                }
                if (secondClass.isSubclass(firstClass, context)) {
                    PyClass nextAfterFirstInMro = null;
                    boolean seenFirst = false;
                    for (PyClass it : secondClass.getAncestorClasses(context)) {
                        if (seenFirst) {
                            nextAfterFirstInMro = it;
                            break;
                        }
                        if (it == firstClass) {
                            seenFirst = true;
                        }
                    }

                    if (nextAfterFirstInMro != null) {
                        return new PyClassTypeImpl(nextAfterFirstInMro, false);
                    }
                }
            }
        }
        // The arguments do not describe a class in the MRO, so `super` gives no class here.
        return PyAnyType.getUnknown();
    }

    private static @Nullable PyType getSuperClassUnionType(PyClass klass, @Nullable TypeEvalContext context) {
        // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
        // super can also delegate to sibling types
        // TODO handle __mro__ here
        PyClass[] supers = klass.getSuperClasses(context);
        if (supers.length > 0) {
            if (supers.length == 1) {
                return new PyClassTypeImpl(supers[0], false);
            }
            List<@Nullable PyType> superTypes = new ArrayList<>();
            for (PyClass aSuper : supers) {
                superTypes.add(new PyClassTypeImpl(aSuper, false));
            }
            return PyUnsafeUnionType.unsafeUnion(superTypes);
        }
        return PyAnyType.getUnknown();
    }

    public static PyArgumentsMapping mapArguments(PyCallExpression call, PyCallableType callableType, TypeEvalContext context) {
        return mapArguments(call, Arrays.asList(call.getArguments()), callableType, context);
    }

    public static PyArgumentsMapping mapArguments(PyElement callSite,
                                                  List<PyExpression> arguments,
                                                  PyCallableType callableType,
                                                  TypeEvalContext context) {
        List<PyCallableArgument> wrappedArguments = toCallableArguments(arguments);
        List<PyCallableParameter> rawParameters = callableType.getParameters(context);
        List<PyCallableParameter> parameters = rawParameters != null ? unpackParameters(rawParameters, wrappedArguments, context) : null;

        if (parameters == null) {
            return PyArgumentsMapping.empty(callSite, arguments);
        }

        return mapArguments(callSite, arguments, wrappedArguments, callableType, parameters, context);
    }

    /**
     * Maps {@code arguments} to {@code parameters} as given, without the unpacking of container parameters.
     * The mapping refers to the same {@link PyCallableParameter} instances as {@code parameters}.
     */
    public static PyArgumentsMapping mapArguments(PyElement callSite,
                                                  List<PyExpression> arguments,
                                                  PyCallableType callableType,
                                                  List<PyCallableParameter> parameters,
                                                  TypeEvalContext context) {
        return mapArguments(callSite, arguments, toCallableArguments(arguments), callableType, parameters, context);
    }

    private static PyArgumentsMapping mapArguments(PyElement callSite,
                                                   List<PyExpression> arguments,
                                                   List<PyCallableArgument> wrappedArguments,
                                                   PyCallableType callableType,
                                                   List<PyCallableParameter> parameters,
                                                   TypeEvalContext context) {
        ArgumentMappingResults mappingResults = analyzeArguments(wrappedArguments, parameters, context);

        List<PyExpression> unmappedArguments = new ArrayList<>();
        for (PyCallableArgument it : mappingResults.getUnmappedArguments()) {
            PyExpression expression = it.getExpression();
            if (expression != null) {
                unmappedArguments.add(expression);
            }
        }

        return new PyArgumentsMapping(callSite,
            arguments,
            callableType,
            mapKeysToExpressions(mappingResults.getMappedParameters()),
            mappingResults.getUnmappedParameters(),
            mappingResults.getUnmappedContainerParameters(),
            unmappedArguments,
            mappingResults.getParametersMappedToVariadicPositionalArguments(),
            mappingResults.getParametersMappedToVariadicKeywordArguments(),
            mapKeysToExpressions(mappingResults.getMappedTupleParameters()));
    }

    private static Map<PyExpression, PyCallableParameter> mapKeysToExpressions(Map<PyCallableArgument, PyCallableParameter> map) {
        Map<PyExpression, PyCallableParameter> result = new LinkedHashMap<>();
        for (Map.Entry<PyCallableArgument, PyCallableParameter> entry : map.entrySet()) {
            result.put(Objects.requireNonNull(entry.getKey().getExpression()), entry.getValue());
        }
        return result;
    }

    public static List<PyArgumentsMapping> mapArguments(PyElement callSite, PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        List<PyArgumentsMapping> result = new ArrayList<>();
        if (callSite instanceof PyCallExpression callExpression) {
            for (PyCallableType it : callExpression.multiResolveCallee(resolveContext)) {
                result.add(mapArguments(callExpression, it, context));
            }
        }
        else if (callSite instanceof PyClass pyClass) {
            for (PyCallableType it : resolveInitSubclassCallee(pyClass, resolveContext)) {
                result.add(mapArguments(pyClass, pyClass.getArguments(), it, context));
            }
        }
        else if (callSite instanceof PyQualifiedExpression qualifiedExpression) {
            for (ResolvedOperator operator : multiResolveOperator(qualifiedExpression, resolveContext)) {
                for (PyCallableType it : PyTypeUtil.getCallableItems(operator.method())) {
                    result.add(mapArguments(qualifiedExpression, operator.arguments(), it, context));
                }
            }
        }
        return result;
    }

    /**
     * Like {@link #mapArguments}, but parameters that get no argument and fall back to their default value
     * are also mapped: the default value expression is used as the key in {@link PyArgumentsMapping#getMappedParameters()}.
     * <p>
     * Note that these keys are declaration-site expressions, not call-site arguments; they are not contained
     * in {@link PyArgumentsMapping#getArguments()}. Only a parameter whose type has generics gets its default mapped.
     * When the callable's file may not be switched to AST, only a default
     * without names is mapped, as an expression parsed from the stub text of the default value.
     * Parameters whose default value has no PSI (e.g., synthetic callables) remain unmapped as in {@link #mapArguments}.
     */
    public static PyArgumentsMapping mapArgumentsWithDefaults(PyCallExpression call,
                                                              PyCallableType callableType,
                                                              TypeEvalContext context) {
        List<PyExpression> arguments = Arrays.asList(call.getArguments());
        List<PyCallableParameter> rawParameters = callableType.getParameters(context);
        if (rawParameters == null) {
            return PyArgumentsMapping.empty(call, arguments);
        }
        // Unpack the parameters the same way mapArguments does, so identity comparisons below work
        List<PyCallableParameter> parameters = unpackParameters(rawParameters, toCallableArguments(arguments), context);
        PyArgumentsMapping mapping = mapArguments(call, arguments, callableType, parameters, context);

        Set<PyCallableParameter> covered = new HashSet<>();
        covered.addAll(mapping.getMappedParameters().values());
        covered.addAll(mapping.getMappedTupleParameters().values());
        covered.addAll(mapping.getParametersMappedToVariadicPositionalArguments());
        covered.addAll(mapping.getParametersMappedToVariadicKeywordArguments());
        List<Pair<PyExpression, PyCallableParameter>> defaultValueArguments = collectDefaultValueArguments(parameters, covered, context);
        if (defaultValueArguments.isEmpty()) {
            return mapping;
        }

        LinkedHashMap<PyExpression, PyCallableParameter> mappedParameters = new LinkedHashMap<>(mapping.getMappedParameters());
        for (Pair<PyExpression, PyCallableParameter> pair : defaultValueArguments) {
            mappedParameters.put(pair.getFirst(), pair.getSecond());
        }

        return new PyArgumentsMapping(mapping.getCallSite(),
            mapping.getArguments(),
            mapping.getCallableType(),
            mappedParameters,
            mapping.getUnmappedParameters(),
            mapping.getUnmappedContainerParameters(),
            mapping.getUnmappedArguments(),
            mapping.getParametersMappedToVariadicPositionalArguments(),
            mapping.getParametersMappedToVariadicKeywordArguments(),
            mapping.getMappedTupleParameters());
    }

    private static List<Pair<PyExpression, PyCallableParameter>> collectDefaultValueArguments(List<PyCallableParameter> parameters,
                                                                                              Set<PyCallableParameter> covered,
                                                                                              TypeEvalContext context) {
        List<Pair<PyExpression, PyCallableParameter>> defaultValueArguments = new ArrayList<>();
        for (PyCallableParameter parameter : parameters) {
            if (!covered.contains(parameter) &&
                parameter.hasDefaultValue() &&
                !parameter.isPositionalContainer() &&
                !parameter.isKeywordContainer()) {
                PyExpression defaultValue = getDefaultValueStubSafe(parameter, context);
                if (defaultValue != null) {
                    defaultValueArguments.add(Pair.create(defaultValue, parameter));
                }
            }
        }
        return defaultValueArguments;
    }

    /**
     * Tells if an {@code ...} default of {@code parameter} only marks that the parameter has a default value.
     * This is the case in a stub file, in an overload signature, and in a protocol method.
     * Such a default does not solve a type variable, so {@link #mapArgumentsWithDefaults} does not map it.
     */
    public static boolean isPlaceholderEllipsisDefault(PyParameter parameter, TypeEvalContext context) {
        if (PyiUtil.isInsideStub(parameter)) {
            return true;
        }
        PyFunction function = PsiTreeUtil.getStubOrPsiParentOfType(parameter, PyFunction.class);
        if (function == null) {
            return false;
        }
        if (PyiUtil.isOverload(function, context)) {
            return true;
        }
        PyClass containingClass = function.getContainingClass();
        return containingClass != null && PyProtocolsKt.isProtocol(containingClass, context);
    }

    private static @Nullable PyExpression getDefaultValueStubSafe(PyCallableParameter callableParameter, TypeEvalContext context) {
        // A default value only solves a type variable, so a parameter type without generics needs no mapped default
        if (!PyTypeChecker.hasGenerics(callableParameter.getArgumentType(context), context)) {
            return null;
        }
        PyParameter psi = callableParameter.getParameter();
        if (psi == null) {
            return callableParameter.getDefaultValue(); // non-PSI parameter: expression is held directly, no file access
        }
        // both hasDefaultValue() and defaultValueText are answered from the stub
        if (!psi.hasDefaultValue()) {
            return null;
        }
        String defaultValueText = psi.getDefaultValueText();
        if (defaultValueText == null) {
            return null;
        }
        if (defaultValueText.equals(PyNames.ELLIPSIS) && isPlaceholderEllipsisDefault(psi, context)) {
            return null;
        }
        if (context.maySwitchToAST(psi)) {
            return psi.getDefaultValue();
        }
        // Without the AST, the default is parsed from its stub text instead,
        // so that its type does not depend on whether the callable's file may be parsed.
        // A name in such a fragment is typed as Any (see PyReferenceExpressionImpl), so only a default without names is mapped.
        PyExpression fragment = PyUtil.createExpressionFromFragment(defaultValueText, psi);
        if (fragment == null) {
            return null;
        }
        return PsiTreeUtil.findChildOfType(fragment, PyReferenceExpression.class, false) == null ? fragment : null;
    }

    /**
     * Resolves the implicit {@code __init_subclass__} invoked when {@code pyClass} is defined.
     * <p>
     * Per PEP 487 the keyword arguments of a class definition (other than {@code metaclass}) are passed to the
     * {@code __init_subclass__} of the <i>parent</i> classes, so the class's own definition (which targets <i>its</i> subclasses)
     * is excluded and the lookup walks the ancestors' MRO, stopping at the first defining class.
     * {@code object.__init_subclass__}, which accepts no arguments, is the implicit fallback.
     * <p>
     * The check is skipped when a custom metaclass is involved, since its {@code __new__}/{@code __init__} may legitimately
     * consume the keyword arguments before they reach {@code __init_subclass__}.
     */
    private static List<PyCallableType> resolveInitSubclassCallee(PyClass pyClass, PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        if (hasCustomMetaClass(pyClass, context)) {
            return List.of();
        }
        // TypedDict class-definition keyword arguments (total, closed, ...) are validated by PyTypedDictInspection.
        if (PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor(pyClass, context)) {
            return List.of();
        }

        for (PyClassLikeType ancestor : pyClass.getAncestorTypes(context)) {
            if (!(ancestor instanceof PyClassType ancestorClassType)) {
                continue;
            }
            List<? extends RatedResolveResult> resolved =
                ancestorClassType.resolveMember(PyNames.INIT_SUBCLASS, null, AccessDirection.READ, resolveContext, false);
            if (resolved == null || resolved.isEmpty()) {
                continue;
            }
            return PyTypeUtil.getCallableItems(PyTypeUtil.getTypeOfBoundMember(ancestorClassType, resolved, context));
        }
        return List.of();
    }

    private static boolean hasCustomMetaClass(PyClass pyClass, TypeEvalContext context) {
        if (!(context.getType(pyClass) instanceof PyClassType classType)) {
            return false;
        }
        PyClassLikeType metaClassType = classType.getMetaClassType(context, true);
        if (metaClassType == null) {
            return false;
        }
        return metaClassType != PyBuiltinCache.getInstance(pyClass).getTypeType();
    }

    public static <T> List<T> getArgumentsMappedToPositionalContainer(Map<T, PyCallableParameter> map) {
        List<T> result = new ArrayList<>();
        for (Map.Entry<T, PyCallableParameter> entry : map.entrySet()) {
            if (entry.getValue().isPositionalContainer()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static <T> List<T> getArgumentsMappedToKeywordContainer(Map<T, PyCallableParameter> map) {
        List<T> result = new ArrayList<>();
        for (Map.Entry<T, PyCallableParameter> entry : map.entrySet()) {
            if (entry.getValue().isKeywordContainer()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static <T> Map<T, PyCallableParameter> getRegularMappedParameters(Map<T, PyCallableParameter> map) {
        Map<T, PyCallableParameter> result = new LinkedHashMap<>();
        for (Map.Entry<T, PyCallableParameter> entry : map.entrySet()) {
            PyCallableParameter it = entry.getValue();
            if (!it.isPositionalContainer() && !it.isKeywordContainer()) {
                result.put(entry.getKey(), it);
            }
        }
        return result;
    }

    public static <T> @Nullable PyCallableParameter getMappedPositionalContainer(Map<T, PyCallableParameter> map) {
        for (PyCallableParameter it : map.values()) {
            if (it.isPositionalContainer()) {
                return it;
            }
        }
        return null;
    }

    public static <T> @Nullable PyCallableParameter getMappedKeywordContainer(Map<T, PyCallableParameter> map) {
        for (PyCallableParameter it : map.values()) {
            if (it.isKeywordContainer()) {
                return it;
            }
        }
        return null;
    }

    /**
     * If {@code classType} is a class instance, resolves its {@code __call__} method.
     * If {@code classType} is a class definition, synthesizes the class constructor.
     */
    public static @Nullable PyType createCallableFromClass(PyClassType classType, PyResolveContext resolveContext) {
        return createCallableFromClass(classType, resolveContext, null);
    }

    /**
     * If {@code classType} is a class instance, resolves its {@code __call__} method.
     * If {@code classType} is a class definition, synthesizes the class constructor.
     */
    public static @Nullable PyType createCallableFromClass(PyClassType classType,
                                                           PyResolveContext resolveContext,
                                                           @Nullable List<ProblemMessage> errors) {
        resolveContext = resolveContext.withoutProperties();
        TypeEvalContext context = resolveContext.getTypeEvalContext();

        if (!classType.isDefinition()) {
            List<? extends RatedResolveResult> dunderCallResolveResults =
                classType.resolveMember(PyNames.CALL, null, AccessDirection.READ, resolveContext);
            PyType callType = PyTypeUtil.getTypeOfBoundMember(classType, dunderCallResolveResults, context, errors);
            return callType instanceof PyClassType callClassType ? createCallableFromClass(callClassType, resolveContext, errors) : callType;
        }

        // https://typing.python.org/en/latest/spec/constructors.html
        // TODO:
        //  To handle a constructor call, the call of class `__new__`, `__init__`, and metaclass `__call__`
        //  methods need to be evaluated using the call arguments. However, the calling function
        //  `getExplicitCalleeType` expects all possible callables regardless of the call site.
        //  This needs to be reworked, though the current implementation, which relies on raw return types,
        //  covers most cases.

        PyClassType strippedClassType = stripDefaultTypeArguments(classType, context);
        List<PyTypeParameterType> classTypeParams = new ArrayList<>();
        for (PyType it : strippedClassType.getTypeArguments()) {
            if (it instanceof PyTypeParameterType typeParameterType) {
                classTypeParams.add(typeParameterType);
            }
        }

        Ref<@Nullable PyType> metaClassCall = resolveMetaClassCallMethod(strippedClassType, resolveContext);
        if (metaClassCall != null) {
            boolean skipNewAndInitEvaluation = isMethodReturnTypeIncompatibleWithClass(strippedClassType, metaClassCall.get(), context);
            if (skipNewAndInitEvaluation) {
                PyType metaClassCallType = metaClassCall.get();
                PyType constructorType = PyTypeUtil.mapCallableType(metaClassCallType, it -> {
                    FunctionBindingResult bindingResult = PyTypeUtil.bindFunction(it, strippedClassType, context);
                    if (bindingResult == null) {
                        return null;
                    }
                    return createConstructor(classTypeParams, bindingResult.boundMethodType(), context);
                });
                if (constructorType == null) {
                    if (errors != null) {
                        errors.add(PyTypeUtil.methodBindingErrorMessage(strippedClassType, metaClassCallType, context));
                    }
                    return PyAnyType.getUnknown();
                }
                return constructorType;
            }
        }

        PyClassType objectType = PyBuiltinCache.getInstance(strippedClassType.getPyClass()).getObjectType();
        Ref<@Nullable PyType> newMethod = strippedClassType.getPyClass().isNewStyleClass(context)
            ? resolveMemberSkippingClass(strippedClassType, PyNames.NEW, objectType, resolveContext)
            : null;

        boolean skipInitEvaluation = newMethod != null && isMethodReturnTypeIncompatibleWithClass(strippedClassType, newMethod.get(), context);
        if (!skipInitEvaluation) {
            Ref<@Nullable PyType> init = resolveMemberSkippingClass(strippedClassType, PyNames.INIT, objectType, resolveContext);
            if (init != null) {
                // Bind `__init__` to class instance and use its specialized `self` parameter type
                // as the return type of the synthesized constructor callable.
                PyClassType selfType = strippedClassType.toInstance();
                PyType initType = init.get();
                PyType constructorType = PyTypeUtil.mapCallableType(initType, it -> {
                    FunctionBindingResult bindingResult = PyTypeUtil.bindFunction(it, selfType, context);
                    if (bindingResult == null) {
                        return null;
                    }
                    return createConstructor(classTypeParams, bindingResult.boundMethodType(), context,
                        bindingResult.specializedFirstParamType());
                });
                if (constructorType == null) {
                    if (errors != null) {
                        errors.add(PyTypeUtil.methodBindingErrorMessage(selfType, initType, context));
                    }
                    return PyAnyType.getUnknown();
                }
                return constructorType;
            }
        }

        if (newMethod != null) {
            PyType newType = newMethod.get();
            PyType constructorType = PyTypeUtil.mapCallableType(newType, it -> {
                FunctionBindingResult bindingResult = PyTypeUtil.bindFunction(it, strippedClassType, context);
                if (bindingResult == null) {
                    return null;
                }
                PyCallableType methodType = bindingResult.boundMethodType();
                PyType returnType = methodType.getReturnType(context);
                // PY-6671
                if (isUnknown(returnType)) {
                    returnType = PyUnionType.createWeakType(strippedClassType.toInstance());
                }
                return createConstructor(classTypeParams, methodType, context, returnType);
            });
            if (constructorType == null) {
                if (errors != null) {
                    errors.add(PyTypeUtil.methodBindingErrorMessage(strippedClassType, newType, context));
                }
                return PyAnyType.getUnknown();
            }
            return constructorType;
        }

        return new PyCallableTypeImpl(classTypeParams, new PyCallableParameterListTypeImpl(List.of()),
            strippedClassType.toInstance(), null, null);
    }

    // TODO: PY-90204
    public static PyClassType stripDefaultTypeArguments(PyClassType classType, TypeEvalContext context) {
        PyClassType genericDef = PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), context);
        if (genericDef == null) {
            return classType;
        }
        if (!classType.isParameterized()) {
            return genericDef.toClass();
        }

        GenericSubstitutions allSubstitutions = PyTypeChecker.collectTypeSubstitutions(classType, context);
        // TODO consulo: consulo.util.lang.ref.SoftReference.deref(Ref) missing
        Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> typeVars = new LinkedHashMap<>();
        for (Map.Entry<PyTypeVarType, @Nullable Ref<@Nullable PyType>> entry : allSubstitutions.getTypeVars().entrySet()) {
            if (!isDefault(entry.getKey(), SoftReference.deref(entry.getValue()))) {
                typeVars.put(entry.getKey(), entry.getValue());
            }
        }
        Map<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> typeVarTuples = new LinkedHashMap<>();
        for (Map.Entry<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> entry : allSubstitutions.getTypeVarTuples().entrySet()) {
            if (!isDefault(entry.getKey(), entry.getValue())) {
                typeVarTuples.put(entry.getKey(), entry.getValue());
            }
        }
        Map<PyParamSpecType, @Nullable PyCallableParameterVariadicType> paramSpecs = new LinkedHashMap<>();
        for (Map.Entry<PyParamSpecType, @Nullable PyCallableParameterVariadicType> entry : allSubstitutions.getParamSpecs().entrySet()) {
            if (!isDefault(entry.getKey(), entry.getValue())) {
                paramSpecs.put(entry.getKey(), entry.getValue());
            }
        }
        GenericSubstitutions nonDefaultSubstitutions = new GenericSubstitutions(
            typeVars,
            typeVarTuples,
            paramSpecs,
            allSubstitutions.getSelfType()
        );
        PyType result = PyTypeChecker.substitute(genericDef, nonDefaultSubstitutions, context);
        return result instanceof PyClassType resultClassType ? resultClassType.toClass() : classType;
    }

    private static boolean isDefault(PyTypeParameterType tp, @Nullable Object actual) {
        PyType defaultType = SoftReference.deref(tp.getDefaultType());
        return defaultType != null && defaultType.equals(actual);
    }

    private static PyCallableType createConstructor(List<PyTypeParameterType> classTypeParams,
                                                    PyCallableType callableType,
                                                    TypeEvalContext context) {
        return createConstructor(classTypeParams, callableType, context, callableType.getReturnType(context));
    }

    private static PyCallableType createConstructor(List<PyTypeParameterType> classTypeParams,
                                                    PyCallableType callableType,
                                                    TypeEvalContext context,
                                                    @Nullable PyType returnType) {
        List<PyTypeParameterType> typeParameters = new ArrayList<>(classTypeParams);
        List<PyTypeParameterType> callableTypeParameters = callableType.getTypeParameters(context);
        if (callableTypeParameters != null) {
            typeParameters.addAll(callableTypeParameters);
        }
        return new PyCallableTypeImpl(
            typeParameters,
            callableType.getParametersType(context),
            returnType,
            callableType.getCallable(),
            callableType.getModifier()
        );
    }

    private static boolean isReturnTypeAnnotated(PyCallableType type, TypeEvalContext context) {
        PyCallable callable = type.getCallable();
        if (callable instanceof PyFunction function) {
            var returnTypeAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(function, context);
            return returnTypeAnnotation != null;
        }
        return false;
    }

    private static @Nullable Ref<@Nullable PyType> resolveMetaClassCallMethod(PyClassType type, PyResolveContext resolveContext) {
        PyClassLikeType metaClassType = type.getMetaClassType(resolveContext.getTypeEvalContext(), true);
        if (!(metaClassType instanceof PyClassType metaClassClassType)) {
            return null;
        }

        PyClassType typeType = PyBuiltinCache.getInstance(type.getPyClass()).getTypeType();
        if (metaClassClassType == typeType) {
            return null;
        }

        return resolveMemberSkippingClass(metaClassClassType, PyNames.CALL, typeType, resolveContext);
    }

    private static @Nullable Ref<@Nullable PyType> resolveMemberSkippingClass(PyClassType type,
                                                                              String name,
                                                                              @Nullable PyClassType skipMembersOf,
                                                                              PyResolveContext resolveContext) {
        List<? extends RatedResolveResult> resolveResults = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
        if (resolveResults == null || resolveResults.isEmpty()) {
            return null;
        }
        if (skipMembersOf != null) {
            PyClass containingClass = PyTypeUtil.getContainingClass(resolveResults);
            if (containingClass == skipMembersOf.getPyClass()) {
                return null;
            }
        }
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        PyType memberType = PyTypeUtil.getTypeOfMember(resolveResults, context);
        return Ref.create(PyTypeUtil.specializeMemberType(type, type, memberType, context));
    }

    /**
     * Returns true if a call of {@code classType} gives no instance of it,
     * because a metaclass {@code __call__} or a {@code __new__} declares another return type.
     */
    private static boolean constructsAnotherType(PyClassType classType, PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        Ref<@Nullable PyType> metaClassCall = resolveMetaClassCallMethod(classType, resolveContext);
        if (metaClassCall != null && isMethodReturnTypeIncompatibleWithClass(classType, metaClassCall.get(), context)) {
            return true;
        }
        if (!classType.getPyClass().isNewStyleClass(context)) {
            return false;
        }
        PyClassType objectType = PyBuiltinCache.getInstance(classType.getPyClass()).getObjectType();
        Ref<@Nullable PyType> newMethod = resolveMemberSkippingClass(classType, PyNames.NEW, objectType, resolveContext);
        if (newMethod == null) {
            return false;
        }
        return isMethodReturnTypeIncompatibleWithClass(classType, newMethod.get(), context);
    }

    private static boolean isMethodReturnTypeIncompatibleWithClass(PyClassType classType,
                                                                   @Nullable PyType methodType,
                                                                   TypeEvalContext context) {
        PyClassType instanceType = classType.toInstance();
        return PyTypeUtil.getCallableItems(methodType).stream().anyMatch(callableType ->
            isReturnTypeAnnotated(callableType, context) &&
                PyTypeUtil.toStream(callableType.getReturnType(context)).anyMatch(it ->
                    isAny(it) || it instanceof PyNeverType || !PyTypeChecker.match(instanceType, it, context)
                )
        );
    }

    private static boolean isLegacyPositionalOnly(PyCallableParameter parameter) {
        return !parameter.isSelf() && parameter.getProtectionLevel() == ProtectionLevel.PRIVATE;
    }

    public static ArgumentMappingResults analyzeArguments(List<PyCallableArgument> arguments,
                                                          PyCallableParameterListType parametersType,
                                                          TypeEvalContext context) {
        List<PyCallableParameter> parameters = unpackParameters(parametersType.getParameters(), arguments, context);
        return analyzeArguments(arguments, parameters, context);
    }

    public static ArgumentMappingResults analyzeArguments(List<PyCallableArgument> arguments,
                                                          List<PyCallableParameter> parameters,
                                                          TypeEvalContext context) {
        boolean hasSlashParameter = parameters.stream().anyMatch(PyCallableParameter::isPositionOnlySeparator);
        PyCallableParameter firstExplicitParam = null;
        for (PyCallableParameter it : parameters) {
            if (!it.isSelf()) {
                firstExplicitParam = it;
                break;
            }
        }
        boolean oldStylePositionalOnly = firstExplicitParam != null && isLegacyPositionalOnly(firstExplicitParam);
        boolean positionalOnlyMode = hasSlashParameter || oldStylePositionalOnly;
        boolean keywordOnlyMode = false;
        boolean mappedVariadicArgumentsToParameters = false;
        LinkedHashMap<PyCallableArgument, PyCallableParameter> mappedParameters = new LinkedHashMap<>();
        List<PyCallableParameter> unmappedParameters = new ArrayList<>();
        List<PyCallableParameter> unmappedContainerParameters = new ArrayList<>();
        List<PyCallableArgument> unmappedArguments = new ArrayList<>();
        List<PyCallableParameter> parametersMappedToVariadicKeywordArguments = new ArrayList<>();
        List<PyCallableParameter> parametersMappedToVariadicPositionalArguments = new ArrayList<>();
        Map<PyCallableArgument, PyCallableParameter> tupleMappedParameters = new LinkedHashMap<>();

        PositionalArgumentsAnalysisResults positionalResults = filterPositionalAndVariadicArguments(arguments, context);
        List<PyCallableArgument> keywordArguments = filter(arguments, it -> it.getExpression() instanceof PyKeywordArgument);
        List<PyCallableArgument> variadicPositionalArguments = positionalResults.variadicPositionalArguments;
        Set<PyCallableArgument> positionalComponentsOfVariadicArguments = new HashSet<>(positionalResults.componentsOfVariadicPositionalArguments);
        List<PyCallableArgument> variadicKeywordArguments = filterVariadicKeywordArguments(arguments);

        List<@Nullable PyCallableArgument> allPositionalArguments = positionalResults.allPositionalArguments;
        List<@Nullable PyCallableArgument> reservedForPostArgs = new ArrayList<>();

        for (int index = 0; index < parameters.size(); index++) {
            PyCallableParameter parameter = parameters.get(index);
            PyParameter psi = parameter.getParameter();

            if (parameter.isPositionOnlySeparator()) {
                positionalOnlyMode = false;
            }
            else if (parameter.isKeywordOnlySeparator()) {
                keywordOnlyMode = true;
            }
            else if (psi instanceof PyNamedParameter || psi == null) {
                String parameterName = parameter.getName();
                if (!parameter.isSelf() && !hasSlashParameter && !isLegacyPositionalOnly(parameter)) {
                    positionalOnlyMode = false;
                }
                if (parameter.isPositionalContainer()) {
                    // In a normal Python signature `*args` swallows all remaining positional arguments,
                    // since nothing positional can follow it. However, `*tuple[T1, *tuple[U, ...], T2]`
                    // expansion produces synthetic positional-only parameters AFTER `*args`
                    // (e.g. `[__p0: T1, *args: U, __p1: T2]`).
                    // Reserve trailing positional arguments here so they end up bound to
                    // those post-`*args` parameters in subsequent iterations.
                    int postArgsPositionalCount = (int) parameters.subList(index + 1, parameters.size()).stream()
                        .filter(it -> it.getParameter() == null && isLegacyPositionalOnly(it))
                        .count();
                    int toReserve = Math.min(postArgsPositionalCount, allPositionalArguments.size());
                    if (toReserve > 0) {
                        int reserveFrom = allPositionalArguments.size() - toReserve;
                        reservedForPostArgs.addAll(allPositionalArguments.subList(reserveFrom, allPositionalArguments.size()));
                        for (int i = 0; i < toReserve; i++) {
                            allPositionalArguments.remove(allPositionalArguments.size() - 1);
                        }
                    }

                    for (PyCallableArgument argument : allPositionalArguments) {
                        if (argument != null) {
                            mappedParameters.put(argument, parameter);
                        }
                    }
                    if (variadicPositionalArguments.size() == 1) {
                        mappedParameters.put(variadicPositionalArguments.get(0), parameter);
                    }
                    if (variadicPositionalArguments.size() != 1 && allPositionalArguments.isEmpty()) {
                        unmappedContainerParameters.add(parameter);
                    }
                    allPositionalArguments.clear();
                    variadicPositionalArguments.clear();
                    keywordOnlyMode = true;
                }
                else if (parameter.isKeywordContainer()) {
                    for (PyCallableArgument argument : keywordArguments) {
                        mappedParameters.put(argument, parameter);
                    }
                    for (PyCallableArgument variadicKeywordArg : variadicKeywordArguments) {
                        mappedParameters.put(variadicKeywordArg, parameter);
                    }
                    keywordArguments.clear();
                    variadicKeywordArguments.clear();
                }
                else if (keywordOnlyMode && psi == null && isLegacyPositionalOnly(parameter) || positionalOnlyMode) {
                    // In a normal Python signature, positional-only parameters cannot follow `*args`
                    // (anything after `*args` is keyword-only), so the `keywordOnlyMode` branch only fires
                    // for synthetic positional-only parameters produced by `*tuple[T1, *tuple[U, ...], T2]`
                    // expansion. For those, consume from arguments reserved at the `*args` branch above;
                    // otherwise (no preceding `*args`) consume from the regular positional pool.
                    List<@Nullable PyCallableArgument> source = keywordOnlyMode ? reservedForPostArgs : allPositionalArguments;
                    if (!source.isEmpty()) {
                        PyCallableArgument positionalArgument = source.remove(0);
                        if (positionalArgument != null) {
                            mappedParameters.put(positionalArgument, parameter);
                        }
                        else {
                            // null = placeholder for a value already supplied by a fixed-length `*tuple` spread; no argument to map here.
                            parametersMappedToVariadicPositionalArguments.add(parameter);
                        }
                    }
                    else if (!variadicPositionalArguments.isEmpty()) {
                        parametersMappedToVariadicPositionalArguments.add(parameter);
                        mappedVariadicArgumentsToParameters = true;
                    }
                    else if (!parameter.hasDefaultValue()) {
                        unmappedParameters.add(parameter);
                    }
                }
                else if (keywordOnlyMode) {
                    PyCallableArgument keywordArgument = removeKeywordArgument(keywordArguments, parameterName);
                    if (keywordArgument != null) {
                        mappedParameters.put(keywordArgument, parameter);
                    }
                    else if (!variadicKeywordArguments.isEmpty()) {
                        parametersMappedToVariadicKeywordArguments.add(parameter);
                        mappedVariadicArgumentsToParameters = true;
                    }
                    else if (!parameter.hasDefaultValue()) {
                        unmappedParameters.add(parameter);
                    }
                }
                else if (isParamSpecOrConcatenate(parameter, context)) {
                    for (PyCallableArgument argument : arguments) {
                        mappedParameters.put(argument, parameter);
                    }
                    allPositionalArguments.clear();
                    keywordArguments.clear();
                    variadicPositionalArguments.clear();
                    variadicKeywordArguments.clear();
                }
                else if (!allPositionalArguments.isEmpty()) {
                    PyCallableArgument positionalArgument = allPositionalArguments.remove(0);
                    if (positionalArgument == null) {
                        // null = placeholder for a value already supplied by a fixed-length `*tuple` spread; no argument to map here.
                        parametersMappedToVariadicPositionalArguments.add(parameter);
                    }
                    else {
                        mappedParameters.put(positionalArgument, parameter);
                        if (positionalComponentsOfVariadicArguments.contains(positionalArgument)) {
                            parametersMappedToVariadicPositionalArguments.add(parameter);
                        }
                    }
                }
                else {
                    PyCallableArgument keywordArgument = removeKeywordArgument(keywordArguments, parameterName);
                    if (keywordArgument != null) {
                        mappedParameters.put(keywordArgument, parameter);
                    }
                    else if (!variadicPositionalArguments.isEmpty() || !variadicKeywordArguments.isEmpty()) {
                        if (!variadicPositionalArguments.isEmpty()) {
                            parametersMappedToVariadicPositionalArguments.add(parameter);
                        }
                        if (!variadicKeywordArguments.isEmpty()) {
                            parametersMappedToVariadicKeywordArguments.add(parameter);
                        }
                        mappedVariadicArgumentsToParameters = true;
                    }
                    else if (!parameter.hasDefaultValue()) {
                        unmappedParameters.add(parameter);
                    }
                }
            }
            else if (psi instanceof PyTupleParameter tupleParameter) {
                if (!allPositionalArguments.isEmpty()) {
                    PyCallableArgument positionalArgument = allPositionalArguments.remove(0);
                    if (positionalArgument != null) {
                        tupleMappedParameters.put(positionalArgument, parameter);
                        TupleMappingResults tupleMappingResults = mapComponentsOfTupleParameter(positionalArgument.getExpression(), tupleParameter);
                        for (Map.Entry<PyExpression, PyCallableParameter> entry : tupleMappingResults.parameters.entrySet()) {
                            mappedParameters.put(new PyCallableArgument(entry.getKey()), entry.getValue());
                        }
                        unmappedParameters.addAll(tupleMappingResults.unmappedParameters);
                        for (PyExpression it : tupleMappingResults.unmappedArguments) {
                            unmappedArguments.add(new PyCallableArgument(it));
                        }
                    }
                    else {
                        // null = placeholder for a value already supplied by a fixed-length `*tuple` spread; no argument to map here.
                        mappedVariadicArgumentsToParameters = true;
                    }
                }
                else if (variadicPositionalArguments.isEmpty()) {
                    if (!parameter.hasDefaultValue()) {
                        unmappedParameters.add(parameter);
                    }
                }
                else {
                    mappedVariadicArgumentsToParameters = true;
                }
            }
            else if (!parameter.hasDefaultValue()) {
                unmappedParameters.add(parameter);
            }
        }

        if (mappedVariadicArgumentsToParameters) {
            variadicPositionalArguments.clear();
            variadicKeywordArguments.clear();
        }

        // Drop the `*tuple` placeholder slots: they stand for already-supplied values, not surplus arguments.
        for (PyCallableArgument it : allPositionalArguments) {
            if (it != null) {
                unmappedArguments.add(it);
            }
        }
        unmappedArguments.addAll(keywordArguments);
        unmappedArguments.addAll(variadicPositionalArguments);
        unmappedArguments.addAll(variadicKeywordArguments);

        return new ArgumentMappingResults(mappedParameters, unmappedParameters, unmappedContainerParameters, unmappedArguments,
            parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments,
            tupleMappedParameters);
    }

    /**
     * Like {@link #analyzeArguments}, but also maps each parameter that gets no argument and has a default value.
     * Only a parameter whose type has generics gets its default mapped.
     * The type of the default value becomes the argument of such a parameter.
     * The default value is evaluated in its declaration scope, not at the call site.
     */
    public static Map<PyCallableArgument, PyCallableParameter> analyzeArgumentsWithDefaults(List<PyCallableArgument> arguments,
                                                                                            PyCallableParameterListType parametersType,
                                                                                            TypeEvalContext context) {
        List<PyCallableParameter> parameters = unpackParameters(parametersType.getParameters(), arguments, context);
        ArgumentMappingResults results = analyzeArguments(arguments, parameters, context);
        Set<PyCallableParameter> covered = new HashSet<>();
        covered.addAll(results.getMappedParameters().values());
        covered.addAll(results.getMappedTupleParameters().values());
        covered.addAll(results.getParametersMappedToVariadicPositionalArguments());
        covered.addAll(results.getParametersMappedToVariadicKeywordArguments());
        List<Pair<PyExpression, PyCallableParameter>> defaultValueArguments = collectDefaultValueArguments(parameters, covered, context);
        if (defaultValueArguments.isEmpty()) {
            return results.getMappedParameters();
        }

        LinkedHashMap<PyCallableArgument, PyCallableParameter> mappedParameters = new LinkedHashMap<>(results.getMappedParameters());
        for (Pair<PyExpression, PyCallableParameter> pair : defaultValueArguments) {
            mappedParameters.put(new PyCallableArgument(context.getType(pair.getFirst())), pair.getSecond());
        }
        return mappedParameters;
    }

    private static boolean isParamSpecOrConcatenate(PyCallableParameter parameter, TypeEvalContext context) {
        // A ParamSpec or a Concatenate is always spelled out in an annotation or in a type comment, so there is no need to evaluate
        // the type of a parameter that has neither. Asking for it would make the mapping of the arguments of a call depend on the type
        // guessed for an unannotated parameter, which is in turn guessed from the arguments of that very call (PY-91387).
        PyParameter psiParameter = parameter.getParameter();
        if (psiParameter instanceof PyNamedParameter namedParameter &&
            namedParameter.getAnnotationValue() == null &&
            namedParameter.getTypeCommentAnnotation() == null) {
            return false;
        }
        PyType type = parameter.getType(context);
        return type instanceof PyParamSpecType || type instanceof PyConcatenateType;
    }

    /**
     * Replaces the {@code *args: P.args, **kwargs: P.kwargs} parameters of {@code callableType} with the parameters that {@code call} gives to {@code P}.
     * <p>
     * The other arguments of {@code call} solve {@code P}, for example the argument for {@code cls: Callable[P, T]}.
     * Returns {@code callableType} if it has no such parameters, or if these arguments do not solve {@code P}.
     */
    public static PyCallableType expandParamSpecArgsKwargs(PyCallExpression call, PyCallableType callableType, TypeEvalContext context) {
        List<PyCallableParameter> parameters = callableType.getParameters(context);
        if (parameters == null) {
            return callableType;
        }
        int argsIndex = -1;
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).isPositionalContainer()) {
                argsIndex = i;
                break;
            }
        }
        if (argsIndex < 0 || argsIndex + 1 >= parameters.size()) {
            return callableType;
        }
        PyCallableParameter args = parameters.get(argsIndex);
        PyCallableParameter kwargs = parameters.get(argsIndex + 1);
        if (!kwargs.isKeywordContainer() || !isParamSpecOrConcatenate(args, context)) {
            return callableType;
        }
        if (!(args.getType(context) instanceof PyParamSpecType paramSpec)) {
            return callableType;
        }
        if (!Objects.equals(kwargs.getType(context), paramSpec)) {
            return callableType;
        }

        PyArgumentsMapping mapping = mapArguments(call, callableType, context);
        Map<PyCallableArgument, PyCallableParameter> regularArguments = new LinkedHashMap<>();
        for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(mapping.getMappedParameters()).entrySet()) {
            regularArguments.put(new PyCallableArgument(entry.getKey()), entry.getValue());
        }
        GenericSubstitutions substitutions = PyTypeChecker.unifyGenericCall(regularArguments, context);
        if (substitutions == null) {
            return callableType;
        }
        if (!(substitutions.getParamSpecs().get(paramSpec) instanceof PyCallableParameterListType paramSpecParameters)) {
            return callableType;
        }

        List<PyCallableParameter> forwardedParameters = paramSpecParameters.getParameters();
        List<PyCallableParameter> prefix = parameters.subList(0, argsIndex);
        // The parameters before a `/` of `P` are positional-only, so the prefix needs no `/` of its own.
        if (forwardedParameters.stream().anyMatch(PyCallableParameter::isPositionOnlySeparator)) {
            prefix = filter(prefix, it -> !it.isPositionOnlySeparator());
        }
        List<PyCallableParameter> expandedParameters = new ArrayList<>(prefix);
        expandedParameters.addAll(forwardedParameters);
        expandedParameters.addAll(parameters.subList(argsIndex + 2, parameters.size()));
        return new PyCallableTypeImpl(callableType.getTypeParameters(context),
            new PyCallableParameterListTypeImpl(expandedParameters),
            callableType.getReturnType(context),
            callableType.getCallable(),
            callableType.getModifier());
    }

    private static <E extends ResolveResult> List<E> forEveryScopeTakeOverloadsOtherwiseImplementations(List<E> es,
                                                                                                         TypeEvalContext context,
                                                                                                         Function<E, @Nullable PsiElement> mapper) {
        if (!containsOverloadsAndImplementations(es, context, mapper)) {
            return es;
        }
        Map<@Nullable Object, List<E>> groups = new LinkedHashMap<>();
        for (E it : es) {
            groups.computeIfAbsent(ScopeUtil.getScopeOwner(mapper.apply(it)), k -> new ArrayList<>()).add(it);
        }
        List<E> result = new ArrayList<>();
        for (List<E> it : groups.values()) {
            result.addAll(takeOverloadsOtherwiseImplementations(it, context, mapper));
        }
        return result;
    }

    private static <E extends ResolveResult> boolean containsOverloadsAndImplementations(Iterable<E> es,
                                                                                         TypeEvalContext context,
                                                                                         Function<E, @Nullable PsiElement> mapper) {
        boolean containsOverloads = false;
        boolean containsImplementations = false;

        for (E element : es) {
            PsiElement mapped = mapper.apply(element);
            if (mapped == null) {
                continue;
            }

            boolean overload = PyiUtil.isOverload(mapped, context);
            containsOverloads = containsOverloads | overload;
            containsImplementations = containsImplementations | !overload;

            if (containsOverloads && containsImplementations) {
                return true;
            }
        }

        return false;
    }

    private static <E extends ResolveResult> List<E> takeOverloadsOtherwiseImplementations(List<E> es,
                                                                                           TypeEvalContext context,
                                                                                           Function<E, @Nullable PsiElement> mapper) {
        if (!containsOverloadsAndImplementations(es, context, mapper)) {
            return es;
        }

        return filter(es, it -> {
            PsiElement mapped = mapper.apply(it);
            return mapped != null && (PyiUtil.isInsideStub(mapped) || PyiUtil.isOverload(mapped, context));
        });
    }

    private static boolean matchesByArgumentTypes(PyCallableType function, List<PyCallableArgument> arguments, TypeEvalContext context) {
        PyCodeInsightCounters.inc(Counter.OVERLOAD_CANDIDATES_CHECKED);
        List<PyCallableParameter> rawParameters = function.getParameters(context);
        if (rawParameters == null) {
            return true;
        }
        List<PyCallableParameter> parameters = unpackParameters(rawParameters, arguments, context);
        ArgumentMappingResults mapping = analyzeArguments(arguments, parameters, context);
        return mapping.getUnmappedParameters().isEmpty() &&
            mapping.getUnmappedArguments().isEmpty() &&
            PyTypeChecker.unifyGenericCall(mapping.getMappedParameters(), context) != null;
    }

    private static TupleMappingResults mapComponentsOfTupleParameter(@Nullable PyExpression argument, PyTupleParameter parameter) {
        List<PyCallableParameter> unmappedParameters = new ArrayList<>();
        List<PyExpression> unmappedArguments = new ArrayList<>();
        Map<PyExpression, PyCallableParameter> mappedParameters = new LinkedHashMap<>();
        PyExpression flattenedArgument = PyPsiUtils.flattenParens(argument);
        if (flattenedArgument instanceof PySequenceExpression sequenceExpression) {
            PyExpression[] argumentComponents = sequenceExpression.getElements();
            PyParameter[] parameterComponents = parameter.getContents();
            for (int i = 0; i < parameterComponents.length; i++) {
                PyParameter param = parameterComponents[i];
                if (i < argumentComponents.length) {
                    PyExpression arg = argumentComponents[i];
                    if (arg != null) {
                        if (param instanceof PyNamedParameter) {
                            mappedParameters.put(arg, PyCallableParameterImpl.psi(param));
                        }
                        else if (param instanceof PyTupleParameter tupleParam) {
                            TupleMappingResults nestedResults = mapComponentsOfTupleParameter(arg, tupleParam);
                            mappedParameters.putAll(nestedResults.parameters);
                            unmappedParameters.addAll(nestedResults.unmappedParameters);
                            unmappedArguments.addAll(nestedResults.unmappedArguments);
                        }
                        else {
                            unmappedArguments.add(arg);
                        }
                    }
                    else {
                        unmappedParameters.add(PyCallableParameterImpl.psi(param));
                    }
                }
                else {
                    unmappedParameters.add(PyCallableParameterImpl.psi(param));
                }
            }
            if (argumentComponents.length > parameterComponents.length) {
                for (int i = parameterComponents.length; i < argumentComponents.length; i++) {
                    PyExpression arg = argumentComponents[i];
                    if (arg != null) {
                        unmappedArguments.add(arg);
                    }
                }
            }
        }
        return new TupleMappingResults(mappedParameters, unmappedParameters, unmappedArguments);
    }

    private static @Nullable PyCallableArgument removeKeywordArgument(List<PyCallableArgument> arguments, @Nullable String name) {
        if (name == null) {
            return null;
        }
        PyCallableArgument result = null;
        for (PyCallableArgument it : arguments) {
            if (it.getExpression() instanceof PyKeywordArgument keywordArgument && name.equals(keywordArgument.getKeyword())) {
                result = it;
                break;
            }
        }
        if (result != null) {
            arguments.remove(result);
        }
        return result;
    }

    private static PositionalArgumentsAnalysisResults filterPositionalAndVariadicArguments(List<PyCallableArgument> arguments,
                                                                                           TypeEvalContext context) {
        List<PyCallableArgument> variadicArguments = new ArrayList<>();
        List<@Nullable PyCallableArgument> allPositionalArguments = new ArrayList<>();
        List<PyCallableArgument> componentsOfVariadicPositionalArguments = new ArrayList<>();
        boolean seenVariadicPositionalArgument = false;
        boolean seenVariadicKeywordArgument = false;
        boolean seenKeywordArgument = false;
        for (int index = 0; index < arguments.size(); index++) {
            PyCallableArgument argument = arguments.get(index);
            PyExpression argumentExpression = argument.getExpression();
            if (argumentExpression instanceof PyStarArgument starArgument) {
                if (starArgument.isKeyword()) {
                    seenVariadicKeywordArgument = true;
                }
                else {
                    PsiElement expr = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfType(starArgument, PyExpression.class));
                    if (expr instanceof PySequenceExpression sequenceExpression) {
                        List<PyCallableArgument> elements = toCallableArguments(Arrays.asList(sequenceExpression.getElements()));
                        allPositionalArguments.addAll(elements);
                        componentsOfVariadicPositionalArguments.addAll(elements);
                    }
                    else {
                        // A fixed-length `*x` spread followed by positional arguments occupies known leading positions: reserve
                        // them with `null` placeholders so the following arguments still map to the correct parameters (PY-40735),
                        // e.g. `"2"` binding to `b` in `baz(*xe, "2")` where `xe: tuple[str]`.
                        int knownLength = expr instanceof PyExpression pyExpression ? knownFixedTupleSpreadLength(pyExpression, context) : -1;
                        boolean followedByPositional = false;
                        for (int it = index + 1; it < arguments.size(); it++) {
                            PyExpression next = arguments.get(it).getExpression();
                            if (!(next instanceof PyStarArgument) && !(next instanceof PyKeywordArgument)) {
                                followedByPositional = true;
                                break;
                            }
                        }
                        if (knownLength >= 0 && followedByPositional) {
                            for (int i = 0; i < knownLength; i++) {
                                allPositionalArguments.add(null);
                            }
                        }
                        else {
                            seenVariadicPositionalArgument = true;
                            variadicArguments.add(argument);
                        }
                    }
                }
            }
            else if (argumentExpression instanceof PyKeywordArgument) {
                seenKeywordArgument = true;
            }
            else {
                if (seenKeywordArgument || seenVariadicKeywordArgument || seenVariadicPositionalArgument) {
                    continue;
                }
                allPositionalArguments.add(argument);
            }
        }
        return new PositionalArgumentsAnalysisResults(allPositionalArguments, componentsOfVariadicPositionalArguments, variadicArguments);
    }

    /**
     * Number of values a {@code *operand} spread contributes for a fixed-length tuple, or {@code -1} when the length is indeterminate.
     */
    private static int knownFixedTupleSpreadLength(PyExpression operand, TypeEvalContext context) {
        PyType type = context.getType(operand);
        if (!(type instanceof PyTupleType tupleType) || tupleType.isHomogeneous()) {
            return -1;
        }
        if (tupleType.getElementTypes().stream().anyMatch(it -> it instanceof PyUnpackedTupleType)) {
            return -1;
        }
        return tupleType.getElementCount();
    }

    private static List<PyCallableArgument> filterVariadicKeywordArguments(List<PyCallableArgument> arguments) {
        List<PyCallableArgument> results = new ArrayList<>();
        for (PyCallableArgument argument : arguments) {
            PyExpression expression = argument.getExpression();
            if (expression != null && isVariadicKeywordArgument(expression)) {
                results.add(argument);
            }
        }
        return results;
    }

    public static List<PyCallableParameter> unpackParameters(List<PyCallableParameter> parameters,
                                                             List<PyCallableArgument> arguments,
                                                             TypeEvalContext context) {
        // Has **kwargs unpack in arguments -> expand only tuples (keep **kwargs container for unpacked TypedDict as is)
        if (filterVariadicKeywordArguments(arguments).isEmpty()) {
            return ParamHelper.unpackContainerParameters(parameters, context);
        }
        else {
            return ParamHelper.unpackPositionalContainerParameters(parameters, context);
        }
    }

    public static boolean isVariadicKeywordArgument(PyExpression expression) {
        return expression instanceof PyStarArgument starArgument && starArgument.isKeyword();
    }

    public static boolean isVariadicPositionalArgument(PyExpression expression) {
        return expression instanceof PyStarArgument starArgument && !starArgument.isKeyword();
    }

    /**
     * Returns the overload declaration of this function that best matches the given call expression,
     * or null if no overload matches or overloads are unavailable in the current context.
     *
     * @deprecated Use {@code selectMatchingOverloads(List<PyCallableType>, List<PyCallableArgument>, TypeEvalContext)}
     */
    @Deprecated
    public static @Nullable PyFunction selectMatchingOverload(PyFunction function,
                                                              PyCallExpression callExpression,
                                                              TypeEvalContext context) {
        Set<PyFunction> overloads = new HashSet<>(PyiUtil.getOverloads(function, context));
        if (overloads.isEmpty()) {
            return null;
        }

        List<PyCallableType> overloadTypes = filter(callExpression.multiResolveCallee(PyResolveContext.defaultContext(context)),
            it -> overloads.contains(it.getCallable()));
        List<PyCallableType> matching =
            selectMatchingOverloads(overloadTypes, toCallableArguments(Arrays.asList(callExpression.getArguments())), context);
        if (matching.size() != 1) {
            return null;
        }
        return matching.get(0).getCallable() instanceof PyFunction matchingFunction ? matchingFunction : null;
    }

    public static boolean canQualifyAnImplicitName(PyExpression expression) {
        if (!(expression instanceof PyCallExpression callExpression)) {
            return true;
        }
        PyExpression callee = callExpression.getCallee();
        if (callee instanceof PyReferenceExpression referenceExpression && PyNames.SUPER.equals(referenceExpression.getName())) {
            PsiElement target = referenceExpression.getReference().resolve();
            if (target != null && PyBuiltinCache.getInstance(expression).isBuiltin(target)) {
                return false; // super() of unresolved type
            }
        }
        return true;
    }

    private static List<PyCallableArgument> toCallableArguments(List<? extends PyExpression> expressions) {
        List<PyCallableArgument> result = new ArrayList<>(expressions.size());
        for (PyExpression it : expressions) {
            result.add(new PyCallableArgument(it));
        }
        return result;
    }

    private static <T> List<T> filter(List<T> list, java.util.function.Predicate<? super T> predicate) {
        List<T> result = new ArrayList<>();
        for (T it : list) {
            if (predicate.test(it)) {
                result.add(it);
            }
        }
        return result;
    }

    private static final class TupleMappingResults {
        final Map<PyExpression, PyCallableParameter> parameters;
        final List<PyCallableParameter> unmappedParameters;
        final List<PyExpression> unmappedArguments;

        TupleMappingResults(Map<PyExpression, PyCallableParameter> parameters,
                            List<PyCallableParameter> unmappedParameters,
                            List<PyExpression> unmappedArguments) {
            this.parameters = parameters;
            this.unmappedParameters = unmappedParameters;
            this.unmappedArguments = unmappedArguments;
        }
    }

    private static final class PositionalArgumentsAnalysisResults {
        final List<@Nullable PyCallableArgument> allPositionalArguments;
        final List<PyCallableArgument> componentsOfVariadicPositionalArguments;
        final List<PyCallableArgument> variadicPositionalArguments;

        PositionalArgumentsAnalysisResults(List<@Nullable PyCallableArgument> allPositionalArguments,
                                           List<PyCallableArgument> componentsOfVariadicPositionalArguments,
                                           List<PyCallableArgument> variadicPositionalArguments) {
            this.allPositionalArguments = allPositionalArguments;
            this.componentsOfVariadicPositionalArguments = componentsOfVariadicPositionalArguments;
            this.variadicPositionalArguments = variadicPositionalArguments;
        }
    }
}
