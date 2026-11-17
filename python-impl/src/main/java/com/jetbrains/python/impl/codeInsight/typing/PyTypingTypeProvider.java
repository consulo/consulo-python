// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.dynatrace.hash4j.hashing.HashValue128;
import com.dynatrace.hash4j.hashing.Hashing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyCustomType;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleTypeProviderKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintFile;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.PyTypeRepresentationDialectKt;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.PyTypeRepresentationResolveKt;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.psi.PyFunctionTypeRepresentation;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.PyPsiFacadeImpl;
import com.jetbrains.python.impl.psi.impl.stubs.PyTypingAliasStubType;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyTypingAliasStub;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionManager;
import consulo.container.boot.ContainerPathManager;
import consulo.document.util.TextRange;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.collection.Stack;
import consulo.util.dataholder.Key;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@ExtensionImpl(id = "pyTypingTypeProvider", order = "after pyDataclassTypeProvider, before pythonDocstringTypeProvider")
public class PyTypingTypeProvider extends PyTypeProviderWithCustomContext<PyTypingTypeProvider.Context> {
    @Override
    public @Nullable PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, Context context) {
        // Check for the exact name in advance for performance reasons
        if ("Generic".equals(referenceExpression.getName())) {
            if (resolvesToQualifiedNames(referenceExpression, context.getTypeContext(), GENERIC)) {
                return createTypingGenericType(referenceExpression);
            }
        }
        // Check for the exact name in advance for performance reasons
        if ("Protocol".equals(referenceExpression.getName())) {
            if (resolvesToQualifiedNames(referenceExpression, context.getTypeContext(), PROTOCOL, PROTOCOL_EXT)) {
                return createTypingProtocolType(referenceExpression);
            }
        }
        // Check for the exact name in advance for performance reasons
        if ("Callable".equals(referenceExpression.getName())) {
            if (resolvesToQualifiedNames(referenceExpression, context.getTypeContext(), CALLABLE, CALLABLE_EXT)) {
                return createTypingCallableType(referenceExpression);
            }
        }

        return null;
    }

    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, Context context) {
        PyExpression typeHint = getAnnotationValue(param, context.getTypeContext());
        if (typeHint == null) {
            String paramTypeCommentHint = param.getTypeCommentAnnotation();
            if (paramTypeCommentHint != null) {
                typeHint = PyUtil.createExpressionFromFragment(paramTypeCommentHint, param);
            }
        }
        if (typeHint == null) {
            PyFunctionTypeAnnotation annotation = getFunctionTypeAnnotation(func);
            if (annotation != null) {
                PyExpression funcTypeCommentParamHint = findParamTypeHintInFunctionTypeComment(annotation, param, func);
                if (funcTypeCommentParamHint == null) {
                    return Ref.create(PyAnyType.getUnknown());
                }
                typeHint = funcTypeCommentParamHint;
            }
        }
        if (typeHint == null) {
            return null;
        }
        if (param.isKeywordContainer()) {
            Ref<PyType> type = getTypeFromUnpackOperator(typeHint, context.getTypeContext());
            if (type != null) {
                return type.get() instanceof PyTypedDictType ? type : null;
            }
        }
        if (typeHint instanceof PyReferenceExpression referenceHint && referenceHint.isQualified() &&
            (param.isPositionalContainer() && "args".equals(referenceHint.getReferencedName()) ||
             param.isKeywordContainer() && "kwargs".equals(referenceHint.getReferencedName())
            )
        ) {
            typeHint = Objects.requireNonNull(referenceHint.getQualifier());
        }
        PyType type = PyTypeUtil.derefOrUnknown(getType(typeHint, context));
        if (param.isPositionalContainer() && !(type instanceof PyParamSpecType)) {
            PyType containerType = PyTypeUtil.toPositionalContainerType(param, type);
            return Ref.create(containerType != null ? containerType : PyAnyType.getUnknown());
        }
        if (param.isKeywordContainer() && !(type instanceof PyParamSpecType)) {
            PyType containerType = PyTypeUtil.toKeywordContainerType(param, type);
            return Ref.create(containerType != null ? containerType : PyAnyType.getUnknown());
        }
        if (PyNames.NONE.equals(param.getDefaultValueText())) {
            PyType noneType = PyBuiltinCache.getInstance(param).getNoneType();
            // A type variable that admits `None` is solved by the `None` default, so the parameter type is not widened
            if (PyTypeUtil.asUnionSequence(type)
                .stream()
                .noneMatch(it -> it instanceof PyTypeVarType typeVar && admitsNone(typeVar, noneType, context.getTypeContext()))) {
                return Ref.create(PyUnionType.unionOrUnknown(type, noneType != null ? noneType : PyAnyType.getUnknown()));
            }
        }
        if (context.getTypeContext().maySwitchToAST(param)) {
            PyExpression defaultValue = param.getDefaultValue();
            if (defaultValue != null) {
                PyType defaultType = context.getTypeContext().getType(defaultValue);
                if (defaultType instanceof PySentinelType) {
                    return Ref.create(PyUnionType.unionOrUnknown(type, defaultType));
                }
            }
        }
        return Ref.create(type);
    }

    @Override
    public @Nullable Ref<PyType> getReturnType(PyCallable callable, Context context) {
        if (callable instanceof PyFunction function) {
            PyExpression returnTypeAnnotation = getReturnTypeAnnotation(function, context.getTypeContext());
            if (returnTypeAnnotation != null) {
                Ref<PyType> typeRef = getType(returnTypeAnnotation, context);
                if (typeRef != null) {
                    // Do not use toAsyncIfNeeded, as it also converts Generators. Here we do not need it.
                    if (function.isAsync() && function.isAsyncAllowed() && !function.isGenerator()) {
                        return Ref.create(wrapInCoroutineType(typeRef.get(), function));
                    }
                    return typeRef;
                }
                // Don't rely on other type providers if a type hint is present but cannot be resolved.
                return Ref.create(PyAnyType.getUnknown());
            }
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getCallType(PyFunction function, PyElement callSite, Context context) {
        String functionQName = function.getQualifiedName();

        if (CAST.equals(functionQName) || CAST_EXT.equals(functionQName)) {
            return Optional
                .ofNullable((PyCallExpression) callSite)
                .map(PyCallExpression::getArguments)
                .filter(it -> it.length != 0)
                .map(it -> getType(Objects.requireNonNull(it[0]), context))
                .orElse(null);
        }

        if (functionReturningCallSiteAsAType(function)) {
            return callSite instanceof PyCallSiteExpression callSiteExpression ? getAsClassObjectType(callSiteExpression, context) : null;
        }

        return null;
    }

    // PEP 747: the explicit `TypeForm(...)` constructor. `_SpecialForm` has no `__call__`, so intercept the call here and
    // return a callable whose result is the `TypeForm` value denoting the argument's type expression.
    @Override
    public @Nullable Ref<PyType> prepareCalleeTypeForCall(@Nullable PyType type, PyExpression callee, Context context) {
        if (type instanceof PyClassType classType && (SPECIAL_FORM.equals(classType.getClassQName()) || SPECIAL_FORM_EXT.equals(classType.getClassQName()))) {
            PyCallExpression call = PyCallExpressionNavigator.getPyCallExpressionByCallee(callee);
            if (call != null && isTypeForm(resolveToQualifiedNames(callee, context.getTypeContext()), callee)) {
                // TODO: `prepareCalleeTypeForCall` is used to provide all possible callables for a call site
                //  and hence must not rely on arguments list
                PyExpression[] arguments = call.getArguments();
                PyType typeForm = createTypeFormType(callee, arguments.length > 0 ? arguments[0] : null, context);
                if (typeForm == null) {
                    return null;
                }
                // A single parameter, so that the regular argument-list checks report a wrong number of arguments.
                PyCallableParameter parameter = PyCallableParameterImpl.nonPsi(PyAnyType.getAny());
                return Ref.create(new PyCallableTypeImpl(List.of(parameter), typeForm));
            }
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement target, Context context, @Nullable PsiElement anchor) {
        PsiElement referenceTarget = target;
        if (referenceTarget instanceof PyTargetExpression targetExpression) {
            String targetQName = targetExpression.getQualifiedName();

            // Depends on typing.Generic defined as a target expression
            if (GENERIC.equals(targetQName)) {
                return Ref.create(createTypingGenericType(targetExpression));
            }
            // Depends on typing.Protocol defined as a target expression
            if (PROTOCOL.equals(targetQName) || PROTOCOL_EXT.equals(targetQName)) {
                return Ref.create(createTypingProtocolType(targetExpression));
            }
            // Depends on typing.Callable defined as a target expression
            if (CALLABLE.equals(targetQName) || CALLABLE_EXT.equals(targetQName)) {
                return Ref.create(createTypingCallableType(targetExpression));
            }

            PyType collection = getCollection(targetExpression, context.getTypeContext());
            if (collection instanceof PyInstantiableType<?> instantiableType) {
                return Ref.create(instantiableType.toClass());
            }

            PyType typedDictType = getTypedDictTypeForTarget(targetExpression, context.getTypeContext());
            if (typedDictType != null) {
                return Ref.create(typedDictType);
            }

            PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(targetExpression);
            if (assignedValue instanceof PyCallExpression assignedCall && assignedCall.isCalleeText("TypeVar", "TypeVarTuple", "ParamSpec")) {
                for (PsiElement element : tryResolving(Objects.requireNonNull(assignedCall.getCallee()), context.getTypeContext())) {
                    if (element instanceof PyClass pyClass && TYPE_PARAMETER_FACTORIES.contains(pyClass.getQualifiedName())) {
                        PyType classType = context.getTypeContext().getType(pyClass);
                        if (classType instanceof PyClassType pyClassType) {
                            return Ref.create(pyClassType.toInstance());
                        }
                    }
                }
            }

            if (isExplicitTypeAlias(targetExpression, context.getTypeContext())) {
                if (assignedValue != null) {
                    PyType type = deref(getType(assignedValue, context));
                    if (type instanceof PyInstantiableType<?> instantiableType) {
                        return Ref.create(instantiableType.toClass());
                    }
                }
                return Ref.create(PyAnyType.getUnknown());
            }

            // Without the AST, only the stub has the value of an implicit type alias such as `Alias = Base[T, int]`
            if (assignedValue != null &&
                targetExpression.getStub() != null &&
                targetExpression.getStub().getCustomStub(PyTypingAliasStub.class) != null &&
                !context.getTypeContext().maySwitchToAST(targetExpression)) {
                PyType type = deref(getType(assignedValue, context));
                if (type instanceof PyInstantiableType<?> instantiableType) {
                    return Ref.create(instantiableType.toClass());
                }
            }

            // Return a type from an immediate type hint, e.g., from a syntactic annotation for
            //
            // x: int = ...
            //
            // or find the "root" declaration and get a type from a type hint there, e.g.
            //
            // x: int
            // x = ...
            //
            // or
            //
            // class C:
            //     attr: int
            //     def __init__(self, x):
            //         self.attr = x
            //
            // self.attr = ...

            // assignments "inst.attr = ..." are not preserved in stubs anyway. See PyTargetExpressionElementType.shouldCreateStub.
            if (targetExpression.isQualified() && context.getTypeContext().maySwitchToAST(targetExpression)) {
                PsiElement resolved = targetExpression.getReference(PyResolveContext.defaultContext(context.getTypeContext())).resolve();
                if (resolved instanceof PyTargetExpression resolvedTarget && PyUtil.isAttribute(resolvedTarget)) {
                    targetExpression = resolvedTarget;
                    referenceTarget = resolvedTarget;
                }
            }

            Ref<PyType> annotatedType = getTypeFromTypeHint(targetExpression, context);
            if (annotatedType != null) {
                return annotatedType;
            }

            String name = targetExpression.getReferencedName();
            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(targetExpression);
            if (name == null || scopeOwner == null) {
                return null;
            }

            String qName = targetExpression.getQualifiedName();

            if (qName != null && name.equals(name.toUpperCase(Locale.ROOT)) && context.getTypeContext().maySwitchToAST(targetExpression)) {
                PyExpression assignedValue2 = targetExpression.findAssignedValue();
                if (assignedValue2 != null) {
                    PyType type = context.getTypeContext().getType(assignedValue2);
                    if (PyTypeUtilKt.isObject(type)) {
                        return Ref.create(new PySentinelType(name, qName, ((PyClassType) type).getPyClass()));
                    }
                }
            }

            PyClass pyClass = targetExpression.getContainingClass();

            if (targetExpression.isQualified()) {
                if (pyClass != null && scopeOwner instanceof PyFunction) {
                    PyResolveContext resolveContext = PyResolveContext.defaultContext(context.getTypeContext());

                    boolean isInstanceAttribute;
                    if (context.getTypeContext().maySwitchToAST(targetExpression)) {
                        isInstanceAttribute =
                            StreamEx.of(PyUtil.multiResolveTopPriority(Objects.requireNonNull(targetExpression.getQualifier()), resolveContext))
                                .select(PyParameter.class)
                                .filter(PyParameter::isSelf)
                                .anyMatch(it -> PsiTreeUtil.getParentOfType(
                                    it,
                                    PyFunction.class
                                ) == scopeOwner);
                    }
                    else {
                        isInstanceAttribute = PyUtil.isInstanceAttribute(targetExpression);
                    }
                    if (!isInstanceAttribute) {
                        return null;
                    }
                    // Set isDefinition=true to start searching right from the class level.
                    Ref<PyType> memberType =
                        getMemberTypeForClassType(context, targetExpression, name, resolveContext, false, new PyClassTypeImpl(pyClass, true));
                    if (memberType != null) {
                        return memberType;
                    }

                    for (PyClass ancestor : pyClass.getAncestorClasses(resolveContext.getTypeEvalContext())) {
                        Ref<PyType> ancestorMemberType = getMemberTypeForClassType(
                            context,
                            targetExpression,
                            name,
                            resolveContext,
                            true,
                            new PyClassTypeImpl(ancestor, false)
                        );
                        if (ancestorMemberType != null) {
                            return ancestorMemberType;
                        }
                    }

                    return null;
                }
            }
            else {
                if (context.getTypeContext().maySwitchToAST(targetExpression)) {
                    PyResolveContext resolveContext = PyResolveContext.defaultContext(context.getTypeContext());
                    Set<PyTargetExpression> visited = new HashSet<>();
                    PsiElement current = targetExpression;
                    while (current instanceof PyTargetExpression currentTarget && visited.add(currentTarget)) {
                        List<ResolveResult> resolveResults = new ArrayList<>();
                        for (ResolveResult result : currentTarget.getReference(resolveContext).multiResolve(false)) {
                            if (result.getElement() != currentTarget) {
                                resolveResults.add(result);
                            }
                        }

                        List<PsiElement> topPriority = PyUtil.filterTopPriorityElements(resolveResults);
                        current = topPriority.isEmpty() ? null : topPriority.get(0);

                        if (current instanceof PyTargetExpression nextTarget) {
                            Ref<PyType> type = getTypeFromTypeHint(nextTarget, context);
                            if (type != null) {
                                return type;
                            }
                        }
                        // TODO Temporarily fixes PY-86904
                        //else if (current is PyNamedParameter) {
                        //  val type = getTypeFromTypeHint(current, context)
                        //  if (type != null) {
                        //    if (current.isPositionalContainer) {
                        //      return Ref(current.toPositionalContainerType(type.get()))
                        //    }
                        //    else if (current.isKeywordContainer) {
                        //      return Ref(current.toKeywordContainerType(type.get()))
                        //    }
                        //    val defaultValue = current.defaultValue
                        //    if (defaultValue != null) {
                        //      val defaultType = context.typeContext.getType(defaultValue)
                        //      if (defaultType is PySentinelType) {
                        //        return Ref(PyUnionType.unionOrUnknown(type.get(), defaultType))
                        //      }
                        //    }
                        //    return type
                        //  }
                        //}
                    }
                }
                else {
                    List<PyTargetExpression> candidates;
                    if (scopeOwner instanceof PyFile pyFile) {
                        candidates = pyFile.getTopLevelAttributes();
                    }
                    else if (scopeOwner instanceof PyClass scopeClass) {
                        candidates = scopeClass.getClassAttributes();
                    }
                    else {
                        candidates = null;
                    }
                    if (candidates != null) {
                        for (PyTargetExpression it : candidates) {
                            if (name.equals(it.getName())) {
                                Ref<PyType> type = getTypeFromTypeHint(it, context);
                                if (type != null) {
                                    return type;
                                }
                            }
                        }
                        return null;
                    }
                }
            }
        }
        if (anchor instanceof PyExpression && referenceTarget instanceof PyClass pyClass && isGeneric(pyClass, context.getTypeContext())) {
            PyClassType parameterizedType = parameterizeClassThroughDefinition(pyClass, List.of(), context);
            if (parameterizedType != null) {
                return Ref.create(parameterizedType.toClass());
            }
        }
        return null;
    }

    @Override
    public @Nullable PyType getGenericType(PyClass cls, Context context) {
        List<PyTypeParameterType> typeParameters = collectTypeParameters(cls, context);
        return typeParameters.isEmpty() ? null : new PyCollectionTypeImpl(cls, false, typeParameters);
    }

    @Override
    public Map<PyType, PyType> getGenericSubstitutions(PyClass cls, Context context) {
        return PyUtil.getParameterizedCachedValue(cls, context, it -> calculateGenericSubstitutions(cls, it));
    }

    private Map<PyType, PyType> calculateGenericSubstitutions(PyClass cls, Context context) {
        if (!isGeneric(cls, context.getTypeContext())) {
            return Collections.emptyMap();
        }
        Map<PyType, PyType> results = new HashMap<>();
        for (PyClassType superClassType : evaluateSuperClassesAsTypeHints(cls, context.getTypeContext())) {
            Map<PyType, PyType> superSubstitutions = RecursionManager.doPreventingRecursion(
                superClassType.getPyClass(),
                false,
                () -> getGenericSubstitutions(superClassType.getPyClass(), context)
            );
            if (superSubstitutions != null) {
                results.putAll(superSubstitutions);
            }
            // TODO Share this logic with PyTypeChecker.collectTypeSubstitutions
            // A TypedDict declares its own parameters; collectTypeParameters would describe those of `dict` here.
            // Only a specialized base is read that way: an unspecialized one leaves its parameters open rather than binding them to Any.
            PyTypedDictType specializedTypedDictBase =
                superClassType instanceof PyTypedDictType typedDictType && typedDictType.isParameterized() ? typedDictType : null;
            List<? extends PyType> declaredTypeParameters =
                specializedTypedDictBase != null ? specializedTypedDictBase.getDeclaredTypeParameters() : null;
            List<? extends PyType> superTypeParameters = declaredTypeParameters != null
                ? declaredTypeParameters
                : collectTypeParameters(superClassType.getPyClass(), context);
            List<PyType> superTypeArguments = superClassType.getTypeArguments();
            PyTypeParameterMapping mapping =
                PyTypeParameterMapping.mapByShape(
                    superTypeParameters, superTypeArguments, PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
                    PyTypeParameterMapping.Option.USE_DEFAULTS
                );
            if (mapping != null) {
                for (Couple<PyType> pair : mapping.getMappedTypes()) {
                    PyType expectedType = pair.getFirst();
                    PyType actualType = pair.getSecond();
                    if (!Objects.equals(expectedType, actualType)) {
                        results.put(expectedType, actualType);
                    }
                }
            }
        }
        return results;
    }

    public record GeneratorTypeDescriptor(
        @Nullable PyType yieldType,
        @Nullable PyType sendType,
        @Nullable PyType returnType,
        boolean isAsync
    ) {
        /**
         * Extracts type parameters from typing.Generator and typing.AsyncGenerator
         */
        public static @Nullable GeneratorTypeDescriptor fromGenerator(@Nullable PyType type) {
            if (!(type instanceof PyClassType classType)) {
                return null;
            }

            String qName = classType.getClassQName();
            if (qName == null) {
                return null;
            }

            boolean isAsync = ASYNC_GENERATOR.equals(qName);
            if (!isAsync && !GENERATOR.equals(qName)) {
                return null;
            }

            PyType builtinNoneType = PyBuiltinCache.getInstance(classType.getPyClass()).getNoneType();
            PyType noneType = builtinNoneType != null ? builtinNoneType : PyAnyType.getUnknown();

            PyType yieldType = PyAnyType.getUnknown();
            PyType sendType = noneType;
            PyType returnType = isAsync ? PyAnyType.getUnknown() : noneType;
            if (classType.isParameterized()) {
                List<PyType> typeArguments = classType.getTypeArguments();
                yieldType = typeArguments.size() > 0 ? typeArguments.get(0) : PyAnyType.getUnknown();
                sendType = typeArguments.size() > 1 ? typeArguments.get(1) : sendType;
                returnType = typeArguments.size() > 2 ? typeArguments.get(2) : returnType;
            }
            return new GeneratorTypeDescriptor(yieldType, sendType, returnType, isAsync);
        }

        /**
         * Unlike {@link #fromGenerator}, this method can also extract a yield type from Protocol types like typing.Iterable
         */
        public static @Nullable GeneratorTypeDescriptor fromGeneratorOrProtocol(@Nullable PyType type, TypeEvalContext context) {
            if (!(type instanceof PyClassType classType)) {
                return null;
            }

            GeneratorTypeDescriptor desc = fromGenerator(classType);
            if (desc != null) {
                return desc;
            }

            if (PyProtocolsKt.isProtocol(classType, context)) {
                PyType yieldType;

                PyType syncUpcast = PyTypeUtil.convertToType(classType, ITERABLE, classType.getPyClass(), context);
                if (syncUpcast instanceof PyClassType syncClassType && syncClassType.isParameterized()) {
                    yieldType = syncClassType.getIteratedItemType();
                    return new GeneratorTypeDescriptor(yieldType, PyAnyType.getUnknown(), PyAnyType.getUnknown(), false);
                }
                PyType asyncUpcast = PyTypeUtil.convertToType(classType, ASYNC_ITERABLE, classType.getPyClass(), context);
                if (asyncUpcast instanceof PyClassType asyncClassType && asyncClassType.isParameterized()) {
                    yieldType = asyncClassType.getIteratedItemType();
                    return new GeneratorTypeDescriptor(yieldType, PyAnyType.getUnknown(), PyAnyType.getUnknown(), true);
                }

                // Here we try to understand a yield type by return type of __next__ method of protocol specified in annotation.
                // We cannot use convertToType with typing.Iterator here, as it inherits from typing.Iterable
                // and requires both __iter__ and __next__, while it should be possible to decide the yield type only by __next__.
                // TODO: unify logic with PyTargetExpressionImpl.getIterationType (PY-82453)
                PyFunction next = classType.getPyClass().findMethodByName(PyNames.DUNDER_NEXT, true, context);
                if (next != null) {
                    yieldType = context.getReturnType(next);
                    yieldType = PyTypeChecker.substitute(yieldType, PyTypeChecker.unifyReceiver(classType, context), context);
                    return new GeneratorTypeDescriptor(yieldType, PyAnyType.getUnknown(), PyAnyType.getUnknown(), false);
                }

                PyFunction anext = classType.getPyClass().findMethodByName(PyNames.ANEXT, true, context);
                if (anext != null) {
                    yieldType = deref(unwrapCoroutineReturnType(context.getReturnType(anext)));
                    yieldType = PyTypeChecker.substitute(yieldType, PyTypeChecker.unifyReceiver(classType, context), context);
                    return new GeneratorTypeDescriptor(yieldType, PyAnyType.getUnknown(), PyAnyType.getUnknown(), true);
                }
            }
            return null;
        }
    }

    @Override
    protected <T> T withCustomContext(TypeEvalContext context, Function<Context, T> delegate) {
        return staticWithCustomContext(context, delegate);
    }

    // @ApiStatus.Internal
    public static final class Context {
        private final TypeEvalContext typeContext;
        private final boolean typeRepresentationMode;
        private final Stack<PyQualifiedNameOwner> typeAliasStack = new Stack<>();
        private boolean isComputeTypeParameterScopeEnabled = true;

        public Context(TypeEvalContext typeContext) {
            this(typeContext, false);
        }

        public Context(TypeEvalContext typeContext, boolean typeRepresentationMode) {
            this.typeContext = typeContext;
            this.typeRepresentationMode = typeRepresentationMode;
            recomputeStrongHashValue();
        }

        public TypeEvalContext getTypeContext() {
            return typeContext;
        }

        public boolean getTypeRepresentationMode() {
            return typeRepresentationMode;
        }

        public Stack<PyQualifiedNameOwner> getTypeAliasStack() {
            return typeAliasStack;
        }

        public boolean isComputeTypeParameterScopeEnabled() {
            return isComputeTypeParameterScopeEnabled;
        }

        // Explicit API for manipulating type alias stack
        public boolean containsTypeAlias(PyQualifiedNameOwner alias) {
            return typeAliasStack.contains(alias);
        }

        public void addTypeAlias(PyQualifiedNameOwner alias) {
            typeAliasStack.add(alias);
            recomputeStrongHashValue();
        }

        public void removeTypeAlias(PyQualifiedNameOwner alias) {
            typeAliasStack.remove(alias);
            recomputeStrongHashValue();
        }

        public boolean isTypeAliasStackEmpty() {
            return typeAliasStack.isEmpty();
        }

        public @Nullable PyQualifiedNameOwner peekTypeAlias() {
            return typeAliasStack.isEmpty() ? null : typeAliasStack.peek();
        }

        /**
         * Runs {@code action} with an empty type alias stack and then restores the stack.
         */
        public <T> T withEmptyTypeAliasStack(Supplier<T> action) {
            List<PyQualifiedNameOwner> saved = new ArrayList<>(typeAliasStack);
            typeAliasStack.clear();
            recomputeStrongHashValue();
            try {
                return action.get();
            }
            finally {
                typeAliasStack.clear();
                typeAliasStack.addAll(saved);
                recomputeStrongHashValue();
            }
        }

        public boolean setComputeTypeParameterScopeEnabled(boolean value) {
            boolean prev = isComputeTypeParameterScopeEnabled;
            isComputeTypeParameterScopeEnabled = value;
            recomputeStrongHashValue();
            return prev;
        }

        public @Nullable PyType getKnownType(PyExpression expression) {
            if (typeContext.isExternal()) {
                return typeContext.getContextTypeCache().get(Pair.create(expression.getText(), getContextStrongHashValue()));
            }
            return typeContext.getContextTypeCache().get(Pair.create(expression, getContextStrongHashValue()));
        }

        public void assumeType(PyExpression expression, PyType type) {
            if (typeContext.isExternal()) {
                typeContext.getContextTypeCache().put(Pair.create(expression.getText(), getContextStrongHashValue()), type);
            }
            else {
                typeContext.getContextTypeCache().put(Pair.create(expression, getContextStrongHashValue()), type);
            }
        }

        private @Nullable HashValue128 myContextStrongHashValue = null;

        private void recomputeStrongHashValue() {
            List<Object> parts = new ArrayList<>();
            parts.add(isComputeTypeParameterScopeEnabled ? "1" : "0");
            List<@Nullable String> qualifiedNames = new ArrayList<>();
            for (PyQualifiedNameOwner it : typeAliasStack) {
                qualifiedNames.add(it.getQualifiedName());
            }
            parts.add(qualifiedNames);
            StringJoiner joiner = new StringJoiner("#");
            for (Object part : parts) {
                joiner.add(String.valueOf(part));
            }
            myContextStrongHashValue = Hashing.xxh3_128().hashCharsTo128Bits(joiner.toString());
        }

        private HashValue128 getContextStrongHashValue() {
            return Objects.requireNonNull(myContextStrongHashValue);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Context context = (Context) other;
            return typeContext.equals(context.typeContext);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeContext);
        }
    }

    public static final String TYPING = "typing";

    public static final String GENERATOR = "typing.Generator";
    public static final String ASYNC_GENERATOR = "typing.AsyncGenerator";
    public static final String COROUTINE = "typing.Coroutine";
    public static final String AWAITABLE = "typing.Awaitable";
    public static final String NAMEDTUPLE = "typing.NamedTuple";
    public static final String TYPED_DICT = "typing.TypedDict";
    public static final String TYPED_DICT_EXT = "typing_extensions.TypedDict";
    public static final String TYPE_GUARD = "typing.TypeGuard";
    public static final String TYPE_GUARD_EXT = "typing_extensions.TypeGuard";
    public static final String TYPE_IS = "typing.TypeIs";
    public static final String TYPE_IS_EXT = "typing_extensions.TypeIs";
    public static final String TYPE_FORM = "typing.TypeForm";
    public static final String TYPE_FORM_EXT = "typing_extensions.TypeForm";
    public static final String GENERIC = "typing.Generic";
    public static final String PROTOCOL = "typing.Protocol";
    public static final String PROTOCOL_EXT = "typing_extensions.Protocol";
    public static final String TYPE = "typing.Type";
    public static final String ANY = "typing.Any";
    public static final String NEW_TYPE = "typing.NewType";
    public static final String NEW_TYPE_EXT = "typing_extensions.NewType";
    public static final String CALLABLE = "typing.Callable";
    public static final String CALLABLE_EXT = "typing_extensions.Callable";
    public static final String MAPPING = "typing.Mapping";
    public static final String MAPPING_GET = "typing.Mapping.get";
    private static final String LIST = "typing.List";
    private static final String DICT = "typing.Dict";
    private static final String DEFAULT_DICT = "typing.DefaultDict";
    private static final String ORDERED_DICT = "typing.OrderedDict";
    private static final String SET = "typing.Set";
    private static final String FROZEN_SET = "typing.FrozenSet";
    private static final String COUNTER = "typing.Counter";
    private static final String DEQUE = "typing.Deque";
    private static final String TUPLE = "typing.Tuple";
    public static final String CLASS_VAR = "typing.ClassVar";
    public static final String TYPE_VAR = "typing.TypeVar";
    public static final String TYPE_VAR_EXT = "typing_extensions.TypeVar";
    public static final String TYPE_VAR_TUPLE = "typing.TypeVarTuple";
    public static final String TYPE_VAR_TUPLE_EXT = "typing_extensions.TypeVarTuple";
    public static final String PARAM_SPEC = "typing.ParamSpec";
    public static final String PARAM_SPEC_EXT = "typing_extensions.ParamSpec";
    private static final String CHAIN_MAP = "typing.ChainMap";
    public static final String UNION = "typing.Union";
    public static final String INTERSECTION_TY_EXT = "ty_extensions.Intersection";
    public static final String CONCATENATE = "typing.Concatenate";
    public static final String CONCATENATE_EXT = "typing_extensions.Concatenate";
    public static final String OPTIONAL = "typing.Optional";
    public static final String NO_RETURN = "typing.NoReturn";
    public static final String NEVER = "typing.Never";
    public static final String NO_RETURN_EXT = "typing_extensions.NoReturn";
    public static final String NEVER_EXT = "typing_extensions.Never";
    public static final String FINAL = "typing.Final";
    public static final String FINAL_EXT = "typing_extensions.Final";
    public static final String LITERAL = "typing.Literal";
    public static final String LITERAL_EXT = "typing_extensions.Literal";
    public static final String LITERALSTRING = "typing.LiteralString";
    public static final String LITERALSTRING_EXT = "typing_extensions.LiteralString";
    public static final String ANNOTATED = "typing.Annotated";
    public static final String ANNOTATED_EXT = "typing_extensions.Annotated";
    public static final String TYPE_ALIAS = "typing.TypeAlias";
    public static final String TYPE_ALIAS_EXT = "typing_extensions.TypeAlias";
    public static final String TYPE_ALIAS_TYPE = "typing.TypeAliasType";
    public static final String TYPE_ALIAS_TYPE_EXT = "typing_extensions.TypeAliasType";
    public static final String SPECIAL_FORM = "typing._SpecialForm";
    public static final String SPECIAL_FORM_EXT = "typing_extensions._SpecialForm";
    public static final String REQUIRED = "typing.Required";
    public static final String REQUIRED_EXT = "typing_extensions.Required";
    public static final String NOT_REQUIRED = "typing.NotRequired";
    public static final String NOT_REQUIRED_EXT = "typing_extensions.NotRequired";
    public static final String READONLY = "typing.ReadOnly";
    public static final String READONLY_EXT = "typing_extensions.ReadOnly";
    public static final String ITERABLE = "typing.Iterable";
    public static final String ITERATOR = "typing.Iterator";
    public static final String ASYNC_ITERABLE = "typing.AsyncIterable";
    public static final String ASYNC_ITERATOR = "typing.AsyncIterator";

    public static final Set<String> TYPE_PARAMETER_FACTORIES = ImmutableSet.of(
        TYPE_VAR, TYPE_VAR_EXT,
        PARAM_SPEC, PARAM_SPEC_EXT,
        TYPE_VAR_TUPLE, TYPE_VAR_TUPLE_EXT
    );

    public static final Set<@Nullable String> TYPE_DICT_QUALIFIERS =
        ImmutableSet.of(REQUIRED, REQUIRED_EXT, NOT_REQUIRED, NOT_REQUIRED_EXT, READONLY, READONLY_EXT);

    public static final String UNPACK = "typing.Unpack";
    public static final String UNPACK_EXT = "typing_extensions.Unpack";

    public static final String SELF = "typing.Self";
    public static final String SELF_EXT = "typing_extensions.Self";

    public static final Pattern TYPE_IGNORE_PATTERN =
        Pattern.compile("#\\s*type:\\s*ignore\\s*(\\[[^]#]*])?($|(\\s.*))", Pattern.CASE_INSENSITIVE);

    /**
     * A `# type: ignore` comment whose code list is still open, as in `# type: ignore[attr-defined, un`.
     */
    public static final Pattern TYPE_IGNORE_UNCLOSED_PATTERN = Pattern.compile("#\\s*type:\\s*ignore\\s*\\[[^]#]*", Pattern.CASE_INSENSITIVE);

    public static final String ASSERT_TYPE = "typing.assert_type";
    public static final String REVEAL_TYPE = "typing.reveal_type";
    public static final String REVEAL_TYPE_EXT = "typing_extensions.reveal_type";
    public static final String CAST = "typing.cast";
    public static final String CAST_EXT = "typing_extensions.cast";

    public static final ImmutableMap<String, String> BUILTIN_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
        .put(LIST, "list")
        .put(DICT, "dict")
        .put(SET, PyNames.SET)
        .put(FROZEN_SET, "frozenset")
        .put(TUPLE, PyNames.TUPLE)
        .build();

    private static final ImmutableMap<String, String> COLLECTIONS_CLASSES = ImmutableMap.<String, String>builder()
        .put(DEFAULT_DICT, "collections.defaultdict")
        .put(ORDERED_DICT, "collections.OrderedDict")
        .put(COUNTER, "collections.Counter")
        .put(DEQUE, "collections.deque")
        .put(CHAIN_MAP, "collections.ChainMap")
        .build();

    public static final ImmutableMap<String, String> TYPING_COLLECTION_CLASSES = ImmutableMap.<String, String>builder()
        .put("list", "List")
        .put("dict", "Dict")
        .put("set", "Set")
        .put("frozenset", "FrozenSet")
        .build();

    public static final ImmutableMap<String, String> TYPING_BUILTINS_GENERIC_ALIASES = ImmutableMap.<String, String>builder()
        .putAll(TYPING_COLLECTION_CLASSES.entrySet())
        .put("type", "Type")
        .put("tuple", "Tuple")
        .build();

    public static final ImmutableSet<String> GENERIC_CLASSES = ImmutableSet.<String>builder() // special forms
        .add(
            TUPLE,
            GENERIC,
            PROTOCOL,
            CALLABLE,
            CALLABLE_EXT,
            TYPE,
            CLASS_VAR,
            FINAL,
            LITERAL,
            ANNOTATED,
            REQUIRED,
            NOT_REQUIRED,
            READONLY
        ) // type aliases
        .add(UNION, OPTIONAL, LIST, DICT, DEFAULT_DICT, ORDERED_DICT, SET, FROZEN_SET, COUNTER, DEQUE, CHAIN_MAP)
        .add(PROTOCOL_EXT, FINAL_EXT, LITERAL_EXT, ANNOTATED_EXT, REQUIRED_EXT, NOT_REQUIRED_EXT, READONLY_EXT)
        .build();

    /**
     * For the following names we shouldn't go further to the RHS of assignments,
     * since they are not type aliases already and in typing.pyi are assigned to
     * some synthetic values.
     */
    public static final ImmutableSet<String> OPAQUE_NAMES = ImmutableSet.<String>builder()
        .add(PyKnownDecorator.TYPING_OVERLOAD.getQualifiedName().toString())
        .add(ANY)
        .add(TYPE_VAR)
        .add(TYPE_VAR_EXT)
        .add(TYPE_VAR_TUPLE)
        .add(TYPE_VAR_TUPLE_EXT)
        .add(GENERIC)
        .add(PARAM_SPEC)
        .add(PARAM_SPEC_EXT)
        .add(CONCATENATE)
        .add(CONCATENATE_EXT)
        .add(INTERSECTION_TY_EXT)
        .add(TUPLE)
        .add(CALLABLE)
        .add(CALLABLE_EXT)
        .add(TYPE)
        .add(PyKnownDecorator.TYPING_NO_TYPE_CHECK.getQualifiedName().toString())
        .add(PyKnownDecorator.TYPING_NO_TYPE_CHECK_EXT.getQualifiedName().toString())
        .add(UNION)
        .add(OPTIONAL)
        .add(LIST)
        .add(DICT)
        .add(DEFAULT_DICT)
        .add(ORDERED_DICT)
        .add(SET)
        .add(FROZEN_SET)
        .add(PROTOCOL, PROTOCOL_EXT)
        .add(CLASS_VAR)
        .add(COUNTER)
        .add(DEQUE)
        .add(CHAIN_MAP)
        .add(NO_RETURN, NO_RETURN_EXT)
        .add(NEVER, NEVER_EXT)
        .add(FINAL, FINAL_EXT)
        .add(LITERAL, LITERAL_EXT)
        .add(TYPED_DICT, TYPED_DICT_EXT)
        .add(ANNOTATED, ANNOTATED_EXT)
        .add(TYPE_ALIAS, TYPE_ALIAS_EXT)
        .add(REQUIRED, REQUIRED_EXT)
        .add(NOT_REQUIRED, NOT_REQUIRED_EXT)
        .add(READONLY, READONLY_EXT)
        .add(SELF, SELF_EXT)
        .add(LITERALSTRING, LITERALSTRING_EXT)
        .build();

    private static final Key<Context> TYPE_HINT_EVAL_CONTEXT = Key.create("TYPE_HINT_EVAL_CONTEXT");

    // TODO consulo: Ref.deref missing
    private static <T> @Nullable T deref(@Nullable Ref<T> ref) {
        return ref == null ? null : ref.get();
    }

    private static @Nullable PyExpression findParamTypeHintInFunctionTypeComment(
        PyFunctionTypeAnnotation annotation,
        PyNamedParameter param,
        PyFunction func
    ) {
        List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
        if (paramTypes.size() == 1 && paramTypes.get(0) instanceof PyEllipsisLiteralExpression) {
            return null;
        }
        int startOffset = omitFirstParamInTypeComment(func, annotation) ? 1 : 0;
        List<PyParameter> funcParams = List.of(func.getParameterList().getParameters());
        int i = funcParams.indexOf(param) - startOffset;
        if (i >= 0 && i < paramTypes.size()) {
            PyExpression paramTypeHint = paramTypes.get(i);
            if (paramTypeHint instanceof PyStarExpression starExpression) {
                return starExpression.getExpression();
            }
            else if (paramTypeHint instanceof PyDoubleStarExpression doubleStarExpression) {
                return doubleStarExpression.getExpression();
            }
            else {
                return paramTypeHint;
            }
        }
        return null;
    }

    public static boolean isGenerator(PyType type) {
        return type instanceof PyClassType classType && classType.isParameterized() && GENERATOR.equals(classType.getClassQName());
    }

    private static PyType createTypingGenericType(PsiElement anchor) {
        return new PyCustomType(GENERIC, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
    }

    private static PyType createTypingProtocolType(PsiElement anchor) {
        return new PyCustomType(PROTOCOL, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
    }

    public static PyType createTypingCallableType(PsiElement anchor) {
        return new PyCustomType(CALLABLE, null, false, true, PyBuiltinCache.getInstance(anchor).getObjectType());
    }

    private static boolean omitFirstParamInTypeComment(PyFunction func, PyFunctionTypeAnnotation annotation) {
        return func.getContainingClass() != null && func.getModifier() != PyAstFunction.Modifier.STATICMETHOD && annotation.getParameterTypeList()
            .getParameterTypes().size() < func.getParameterList().getParameters().length;
    }

    // @ApiStatus.Internal
    public static @Nullable PyExpression getReturnTypeAnnotation(PyFunction function, TypeEvalContext context) {
        PyExpression returnAnnotation = getAnnotationValue(function, context);
        if (returnAnnotation != null) {
            return returnAnnotation;
        }
        PyFunctionTypeAnnotation functionAnnotation = getFunctionTypeAnnotation(function);
        if (functionAnnotation != null) {
            return functionAnnotation.getReturnType();
        }
        return null;
    }

    public static @Nullable PyFunctionTypeAnnotation getFunctionTypeAnnotation(PyFunction function) {
        String comment = function.getTypeCommentAnnotation();
        if (comment == null) {
            return null;
        }
        PyFunctionTypeAnnotationFile file = LanguageCachedValueUtil.getCachedValue(
            function,
            () -> CachedValueProvider.Result.create(
                new PyFunctionTypeAnnotationFile(
                    Objects.requireNonNull(function.getTypeCommentAnnotation()),
                    function
                ), function
            ));
        return file.getAnnotation();
    }

    private static boolean functionReturningCallSiteAsAType(PyFunction function) {
        String name = function.getName();

        if (PyNames.CLASS_GETITEM.equals(name)) {
            return true;
        }
        if (PyNames.GETITEM.equals(name)) {
            PyClass cls = function.getContainingClass();
            if (cls != null) {
                String qualifiedName = cls.getQualifiedName();
                return SPECIAL_FORM.equals(qualifiedName) || SPECIAL_FORM_EXT.equals(qualifiedName);
            }
        }

        return false;
    }

    private static @Nullable PyType getTypedDictTypeForTarget(PyTargetExpression referenceTarget, TypeEvalContext context) {
        if (PyTypedDictTypeProvider.Helper.isTypedDict(referenceTarget, context)) {
            return new PyCustomType(
                TYPED_DICT, null, false, true,
                PyBuiltinCache.getInstance(referenceTarget).getDictType()
            );
        }

        return null;
    }

    private static @Nullable Ref<PyType> getMemberTypeForClassType(
        Context context,
        @Nullable PyTargetExpression target,
        String name,
        PyResolveContext resolveContext,
        boolean isInherited,
        PyClassTypeImpl classType
    ) {
        List<? extends RatedResolveResult> classAttrs =
            classType.resolveMember(name, target, AccessDirection.READ, resolveContext, isInherited);
        if (classAttrs == null) {
            return null;
        }
        return StreamEx.of(classAttrs)
            .map(RatedResolveResult::getElement)
            .select(PyTargetExpression.class)
            .filter(it -> {
                ScopeOwner owner = ScopeUtil.getScopeOwner(it);
                return owner instanceof PyClass || owner instanceof PyFunction;
            })
            .map(it -> getTypeFromTypeHint(it, context))
            .collect(PyTypeUtil.toUnionFromRef());
    }

    private static <T extends PyAnnotationOwner & PyTypeCommentOwner> @Nullable Ref<PyType> getTypeFromTypeHint(T element,
                                                                                                                Context context) {
        PyExpression annotation = getAnnotationValue(element, context.getTypeContext());
        if (annotation != null) {
            return getType(annotation, context);
        }
        String comment = element.getTypeCommentAnnotation();
        if (comment != null) {
            return getVariableTypeCommentType(comment, element, context);
        }
        return null;
    }

    /**
     * Checks that text of a comment starts with `# type:` prefix and returns a trimmed type hint after it.
     * The trailing part is supposed to contain type annotation in PEP 484 compatible format and an optional
     * plain text comment separated from it with another "#".
     * <p>
     * <p>
     * For instance, for `# type: List[int]# comment` it returns `List[int]`.
     * <p>
     * <p>
     * This method cannot return an empty string.
     *
     * @see #getTypeCommentValueRange
     */
    public static @Nullable String getTypeCommentValue(String text) {
        return PyUtilCore.getTypeCommentValue(text);
    }

    /**
     * Returns the corresponding text range for a type hint as returned by {@link #getTypeCommentValue}.
     *
     * @see #getTypeCommentValue
     */
    public static @Nullable TextRange getTypeCommentValueRange(String text) {
        return PyUtilCore.getTypeCommentValueRange(text);
    }

    private static List<PyClassType> evaluateSuperClassesAsTypeHints(PyClass pyClass, TypeEvalContext context) {
        List<PyClassType> results = new ArrayList<>();
        for (PyExpression superClassExpression : getSuperClassExpressions(pyClass)) {
            PyType type = PyTypeUtil.derefOrUnknown(getType(superClassExpression, context));
            if (type instanceof PyClassType classType) {
                results.add(classType);
            }
        }
        return results;
    }

    private static List<PyTypeParameterType> collectTypeParameters(PyClass cls, Context context) {
        if (!isGeneric(cls, context.getTypeContext())) {
            return Collections.emptyList();
        }
        PyTypeParameterList typeParameterList = cls.getTypeParameterList();
        if (typeParameterList != null) {
            List<PyTypeParameter> typeParameters = typeParameterList.getTypeParameters();
            List<PyTypeParameterType> result = new ArrayList<>();
            for (PyTypeParameter it : typeParameters) {
                PyTypeParameterType typeParameterType = getTypeParameterTypeFromTypeParameter(it, context);
                if (typeParameterType != null) {
                    result.add(typeParameterType);
                }
            }
            return result;
        }
        // See https://mypy.readthedocs.io/en/stable/generics.html#defining-sub-classes-of-generic-classes
        List<PySubscriptionExpression> parameterizedSuperClassExpressions = new ArrayList<>();
        for (PyExpression it : getSuperClassExpressions(cls)) {
            if (it instanceof PySubscriptionExpression subscriptionExpression) {
                parameterizedSuperClassExpressions.add(subscriptionExpression);
            }
        }
        PySubscriptionExpression genericOrProtocolBase = null;
        for (PySubscriptionExpression it : parameterizedSuperClassExpressions) {
            if (resolvesToQualifiedNames(it.getOperand(), context.getTypeContext(), GENERIC, PROTOCOL, PROTOCOL_EXT)) {
                genericOrProtocolBase = it;
                break;
            }
        }
        return StreamEx.of(
                genericOrProtocolBase != null ? List.of(genericOrProtocolBase)
                    : parameterizedSuperClassExpressions
            )
            .map(PySubscriptionExpression::getIndexExpression)
            .flatMap(it -> {
                return it instanceof PyTupleExpression tupleExpr ? StreamEx.of(tupleExpr.getElements()) : StreamEx.of(it);
            })
            .nonNull()
            .map(it -> getType(it, context))
            .map(PyTypingTypeProvider::deref)
            .flatMap(it -> {
                PyTypeChecker.Generics typeParams = PyTypeChecker.collectGenerics(it, context.getTypeContext());
                return StreamEx.<PyType>of(typeParams.getTypeVars()).append(typeParams.getTypeVarTuples())
                    .append(StreamEx.of(typeParams.getParamSpecs()));
            })
            .select(PyTypeParameterType.class)
            // A base such as `Base[type[T]]` gives the class object form of `T`. The class has the instance form as its type parameter.
            .map(it -> it instanceof PyTypeVarType typeVar && typeVar.isDefinition() ? (PyTypeParameterType) typeVar.toInstance() : it)
            // The name identifies a type parameter of the class. A base such as `Base["Cls[T]"]` can give `T` another scope owner.
            .distinct(PyTypeParameterType::getName)
            .toList();
    }

    /**
     * If the class' stub is present, return expressions in the base classes list, converting
     * their saved text chunks into {@link PyExpressionCodeFragment} and extracting top-level expressions
     * from them. Otherwise, get superclass expressions directly from AST.
     */
    public static List<PyExpression> getSuperClassExpressions(PyClass pyClass) {
        PyClassStub classStub = pyClass.getStub();
        if (classStub == null) {
            return List.of(pyClass.getSuperClassExpressions());
        }
        return LanguageCachedValueUtil.getCachedValue(pyClass, () -> {
            List<PyExpression> result = new ArrayList<>();
            for (String it : classStub.getSuperClassesText()) {
                PyExpression expression = PyUtil.createExpressionFromFragment(it, pyClass);
                if (expression != null) {
                    result.add(expression);
                }
            }
            return CachedValueProvider.Result.create(
                result,
                PsiModificationTracker.MODIFICATION_COUNT
            );
        });
    }

    public static boolean isGeneric(PyWithAncestors descendant, TypeEvalContext context) {
        if (descendant instanceof PyClass pyClass && pyClass.getTypeParameterList() != null ||
            descendant instanceof PyClassType classType && classType.getPyClass().getTypeParameterList() != null
        ) {
            return true;
        }
        for (PyClassLikeType ancestor : descendant.getAncestorTypes(context)) {
            if (ancestor != null) {
                if (GENERIC_CLASSES.contains(ancestor.getClassQName())) {
                    return true;
                }
                else if (ancestor instanceof PyClassType ancestorClassType &&
                         ancestorClassType.getPyClass().getTypeParameterList() != null
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    public static @Nullable Ref<PyType> getType(PyExpression expression, TypeEvalContext context) {
        return getType(expression, context, false);
    }

    public static @Nullable Ref<PyType> getType(PyExpression expression, TypeEvalContext context, boolean typeRepresentation) {
        return staticWithCustomContext(context, typeRepresentation, it -> getType(expression, it));
    }

    private static @Nullable Ref<PyType> getType(PyExpression expression, Context context) {
        return getType(expression, context, true);
    }

    private static @Nullable Ref<PyType> getType(PyExpression expression, Context context, boolean parameterizeTopLevel) {
        PyType knownType = context.getKnownType(expression);
        if (knownType != null) {
            return Ref.create(knownType);
        }
        for (Pair<@Nullable PyQualifiedNameOwner, PsiElement> pair : tryResolvingWithAliases(expression,
                                                                                             context.getTypeContext(),
                                                                                             context.getTypeRepresentationMode())) {
            Ref<PyType> typeRef = getTypeForResolvedElement(expression, pair.getFirst(), pair.getSecond(), context, parameterizeTopLevel);
            if (typeRef != null) {
                PyType type = typeRef.get();
                PyAnyType.validate(type, expression);
                if (type != null) {
                    context.assumeType(expression, type);
                }
                return typeRef;
            }
        }
        return null;
    }

    private static boolean typeHasOverloadedBitwiseOr(
        PyType type, PyExpression expression, String operator,
        Context context
    ) {
        if (!(type instanceof PyClassType classType)) {
            return false;
        }
        TypeEvalContext typeContext = context.getTypeContext();
        PyClassLikeType metaClassType = classType.getMetaClassType(typeContext, true);
        if (metaClassType == null) {
            return false;
        }
        List<? extends RatedResolveResult> resolved = metaClassType
            .resolveMember(operator, expression, AccessDirection.READ, PyResolveContext.defaultContext(typeContext));
        if (resolved == null || resolved.isEmpty()) {
            return false;
        }

        return StreamEx.of(resolved)
            .map(RatedResolveResult::getElement)
            .nonNull()
            .noneMatch(it -> PyBuiltinCache.getInstance(it).isBuiltin(it));
    }

    /**
     * {@code true} when {@code operand} makes the enclosing `|` an operator call rather than a PEP 604 union:
     * {@code operand}'s metaclass overloads {@code operator}, or {@code operand} is itself a binary `|` whose inner
     * level already declined a union.
     */
    private static boolean isOperatorCallOperand(
        PyExpression operand, String operator, Context context
    ) {
        PyType type = deref(getType(operand, context));
        if (type != null) {
            return typeHasOverloadedBitwiseOr(type, operand, operator, context);
        }
        return operand instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR;
    }

    public static boolean isBitwiseOrUnionAvailable(TypeEvalContext context) {
        PsiFile originFile = context.getOrigin();
        return originFile == null || isBitwiseOrUnionAvailable(originFile);
    }

    public static boolean isBitwiseOrUnionAvailable(PsiElement element) {
        if (LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON310)) {
            return true;
        }

        PsiFile file = element.getContainingFile();
        return file instanceof PyFile pyFile && pyFile.hasImportFromFuture(FutureFeature.ANNOTATIONS) && (file == element || PsiTreeUtil.getParentOfType(
            element,
            PyAnnotation.class,
            false,
            PyStatement.class
        ) != null);
    }

    private static @Nullable Ref<PyType> getTypeForResolvedElement(
        PyExpression typeHint,
        @Nullable PyQualifiedNameOwner alias,
        PsiElement resolved,
        Context context,
        boolean parameterizeTopLevel
    ) {
        if (alias != null) {
            if (context.containsTypeAlias(alias)) {
                // Recursive types are not yet supported
                return null;
            }
            context.addTypeAlias(alias);
        }
        try {
            Ref<PyType> typeHintFromProvider = PyTypeHintProvider.parseTypeHintFromProviders(
                typeHint,
                alias,
                resolved,
                context.getTypeContext()
            );
            if (typeHintFromProvider != null) {
                return typeHintFromProvider;
            }
            Ref<PyType> typeFromParenthesizedExpression = getTypeFromParenthesizedExpression(resolved, context);
            if (typeFromParenthesizedExpression != null) {
                return typeFromParenthesizedExpression;
            }
            // We perform chained resolve only for actual aliases as tryResolvingWithAliases() returns the passed-in
            // expression both when it's not a reference expression and when it's failed to resolve it. Hence, we might
            // hit SOE for mere unresolved references in the latter case.
            if (alias != null) {
                Ref<PyType> typeFromTypeAlias = getTypeFromTypeAlias(alias, typeHint, resolved, context);
                if (typeFromTypeAlias != null) {
                    return typeFromTypeAlias;
                }
            }
            PyType neverType = getNeverType(resolved);
            if (neverType != null) {
                return Ref.create(neverType);
            }
            Ref<PyType> unionType = getUnionType(resolved, context);
            if (unionType != null) {
                return unionType;
            }
            Ref<PyType> intersectionType = getIntersectionType(resolved, context);
            if (intersectionType != null) {
                return intersectionType;
            }
            PyType concatenateType = getConcatenateType(resolved, context);
            if (concatenateType != null) {
                return Ref.create(concatenateType);
            }
            Ref<PyType> optionalType = getOptionalType(resolved, context);
            if (optionalType != null) {
                return optionalType;
            }
            PyType callableType = getCallableType(resolved, context);
            if (callableType != null) {
                return Ref.create(callableType);
            }
            Ref<PyType> classVarType = unwrapTypeModifier(resolved, context, CLASS_VAR);
            if (classVarType != null) {
                return classVarType;
            }
            Ref<PyType> classObjType = getClassObjectType(resolved, context);
            if (classObjType != null) {
                return classObjType;
            }
            Ref<PyType> typeFormType = getTypeFormType(resolved, context);
            if (typeFormType != null) {
                return typeFormType;
            }
            Ref<PyType> finalType = unwrapTypeModifier(resolved, context, FINAL, FINAL_EXT);
            if (finalType != null) {
                return finalType;
            }
            Ref<PyType> annotatedType = getAnnotatedType(resolved, context);
            if (annotatedType != null) {
                return annotatedType;
            }
            Ref<PyType> requiredOrNotRequiredType = getTypedDictSpecialItemType(resolved, context);
            if (requiredOrNotRequiredType != null) {
                return requiredOrNotRequiredType;
            }
            Ref<PyType> literalStringType = getLiteralStringType(resolved, context);
            if (literalStringType != null) {
                return literalStringType;
            }
            Ref<PyType> literalType = getLiteralType(resolved, context);
            if (literalType != null) {
                return literalType;
            }
            Ref<PyType> narrowedType = getNarrowedType(resolved, context);
            if (narrowedType != null) {
                return narrowedType;
            }
            PyType parameterizedType = getParameterizedType(resolved, context);
            if (parameterizedType != null) {
                return Ref.create(parameterizedType);
            }
            PyType collection = getCollection(resolved, context.getTypeContext());
            if (collection != null) {
                return Ref.create(collection);
            }
            PyType typeParameter = getTypeParameterTypeFromDeclaration(resolved, context);
            if (typeParameter != null) {
                return Ref.create(anchorTypeParameter(typeHint, typeParameter, context));
            }
            PyType unpackedType = getUnpackedType(resolved, context.getTypeContext());
            if (unpackedType != null) {
                return Ref.create(unpackedType);
            }
            // The default or the bound of a type parameter can refer to the type parameter itself
            PyType typeParameterType = resolved instanceof PyTypeParameter pyTypeParameter
                ? RecursionManager.doPreventingRecursion(pyTypeParameter, false, () -> {
                return getTypeParameterTypeFromTypeParameter(pyTypeParameter, context);
            })
                : null;
            if (typeParameterType != null) {
                return Ref.create(typeParameterType);
            }
            PyType callableParameterListType = getCallableParameterListType(resolved, context);
            if (callableParameterListType != null) {
                return Ref.create(callableParameterListType);
            }
            PyType stringBasedType = getStringLiteralType(resolved, context);
            if (stringBasedType != null) {
                return Ref.create(stringBasedType);
            }
            Ref<PyType> anyType = getAnyType(resolved, context);
            if (anyType != null) {
                return anyType;
            }
            PyType typedDictType = PyTypedDictTypeProvider.Helper.getTypedDictTypeForResolvedElement(
                resolved,
                context.getTypeContext()
            );
            if (typedDictType != null) {
                return Ref.create(typedDictType);
            }
            Ref<PyType> selfType = getSelfType(resolved, typeHint, context);
            if (selfType != null) {
                return selfType;
            }
            Ref<PyType> noneType = getNoneType(typeHint, resolved);
            if (noneType != null) {
                return noneType;
            }
            PyTypingNewType newType = PyTypingNewTypeTypeProvider.Helper.getNewTypeForResolvedElement(resolved, context.getTypeContext());
            if (newType != null) {
                return Ref.create(newType.toInstance());
            }
            PyNamedTupleType namedTupleType = getNamedTupleType(resolved, context);
            if (namedTupleType != null) {
                return Ref.create(namedTupleType);
            }
            Ref<PyType> classType = getClassType(typeHint, resolved, parameterizeTopLevel, context);
            if (classType != null) {
                return classType;
            }
            Ref<PyType> typeEngineType = getTypeEngineType(typeHint, context);
            if (typeEngineType != null) {
                return typeEngineType;
            }
            return null;
        }
        finally {
            if (alias != null) {
                context.removeTypeAlias(alias);
            }
        }
    }

    private static @Nullable PyNamedTupleType getNamedTupleType(PsiElement element, Context context) {
        if (element instanceof PyClass pyClass) {
            PyNamedTupleType namedTupleType = PyNamedTupleTypeProviderKt.getNamedTupleTypeForClass(pyClass, context.getTypeContext());
            return namedTupleType != null ? namedTupleType.toInstance() : null;
        }
        return null;
    }

    private static @Nullable Ref<PyType> getTypeEngineType(PyExpression typeHint, Context context) {
        if (!context.getTypeRepresentationMode()) {
            return null;
        }
        if ("Unknown".equals(typeHint.getText())) {
            return Ref.create(PyAnyType.getUnknown());
        }
        if (typeHint instanceof PyFunctionTypeRepresentation) {
            PyType result = context.getTypeContext().getType(typeHint);
            if (result != null) {
                return Ref.create(result);
            }
        }
        if (typeHint instanceof PySubscriptionExpression subscriptionExpression) {
            Ref<PyType> moduleType = getModuleType(subscriptionExpression);
            if (moduleType != null) {
                return moduleType;
            }
            Ref<PyType> overloadType = getOverloadType(subscriptionExpression, context);
            if (overloadType != null) {
                return overloadType;
            }
        }
        if (typeHint instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.AT) {
            String name = binaryExpression.getLeftExpression().getText();
            PyExpression scopeExpression = Objects.requireNonNull(binaryExpression.getRightExpression());
            if (name.equals(SELF)) {
                Ref<PyType> type = getType(scopeExpression, context);
                if (type == null) {
                    return null;
                }

                PyType scopeType = type.get();
                if (scopeType instanceof PyClassType scopeClassType) {
                    return Ref.create(new PySelfType(scopeClassType));
                }
            }
            PyQualifiedNameOwner scopeOwner;
            if (scopeExpression instanceof PyReferenceExpression scopeReference && scopeReference.asQualifiedName() != null) {
                QualifiedName qualifiedName = Objects.requireNonNull(scopeReference.asQualifiedName());
                scopeOwner = PyTypeRepresentationResolveKt.resolveTypeRepresentationName(qualifiedName,
                                                                                        scopeReference,
                                                                                        context.getTypeContext(),
                                                                                        PyQualifiedNameOwner.class);
            }
            else {
                scopeOwner = null;
            }
            PyTypeVarTypeImpl result = new PyTypeVarTypeImpl(name, PyAnyType.getUnknown()).withScopeOwner(scopeOwner);
            return Ref.create(result);
        }
        return null;
    }

    private static @Nullable Ref<PyType> getModuleType(PySubscriptionExpression moduleDefinition) {
        String name = moduleDefinition.getRootOperand().getName();
        if (name == null || !name.equals(PyTypeRepresentationDialectKt.PyModuleTypeName)) {
            return null;
        }
        if (!(moduleDefinition.getIndexExpression() instanceof PyReferenceExpression moduleReferenceExpression)) {
            return null;
        }
        String moduleName = moduleReferenceExpression.getName();
        if (moduleName == null) {
            return null;
        }
        Project project = moduleDefinition.getProject();
        List<PyFile> moduleInitFiles = PyModuleNameIndex.findByQualifiedName(QualifiedName.fromDottedString(moduleName),
                                                                             project,
                                                                             GlobalSearchScope.everythingScope(project));
        Path skeletons = Path.of(PythonSdkType.getSkeletonsRootPath(ContainerPathManager.get().getSystemPath()));
        PyFile firstModuleInitFile = null;
        for (PyFile it : moduleInitFiles) {
            if (it != null && !Path.of(it.getVirtualFile().getPath()).startsWith(skeletons)) {
                firstModuleInitFile = it;
                break;
            }
        }
        if (firstModuleInitFile == null) {
            for (PyFile it : moduleInitFiles) {
                if (it != null) {
                    firstModuleInitFile = it;
                    break;
                }
            }
        }
        if (firstModuleInitFile == null) {
            return null;
        }

        return Ref.create(new PyModuleType(firstModuleInitFile));
    }

    private static @Nullable Ref<PyType> getOverloadType(PySubscriptionExpression overloadDefinition, Context context) {
        if (!overloadDefinition.getOperand().getText().equals(PyNames.OVERLOAD_TYPE)) {
            return null;
        }
        PyExpression items = PyPsiUtils.flattenParens(overloadDefinition.getIndexExpression());
        if (!(items instanceof PyTupleExpression tupleExpression)) {
            return null;
        }
        List<PyCallableType> signatures = new ArrayList<>();
        for (PyExpression it : tupleExpression) {
            if (deref(getType(it, context)) instanceof PyCallableType callableType) {
                signatures.add(callableType);
            }
        }
        return Ref.create(new PyOverloadType(signatures, null));
    }

    private static @Nullable Ref<PyType> getIntersectionType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            if (!resolvesToQualifiedNames(subscriptionExpression.getOperand(), context.getTypeContext(), INTERSECTION_TY_EXT)) {
                return null;
            }
            List<@Nullable PyType> memberTypes = getIndexTypes(subscriptionExpression, context);
            // An empty intersection (`Intersection[()]`) is the greatest lower bound of no types, i.e. the top type `object`.
            if (memberTypes.isEmpty()) {
                return Ref.create(PyBuiltinCache.getInstance(subscriptionExpression).getObjectType());
            }
            PyType intersection = PyIntersectionType.intersectionOrTop(memberTypes);
            return intersection != null ? Ref.create(intersection) : null;
        }
        if (!(resolved instanceof PyBinaryExpression binaryExpression) || binaryExpression.getOperator() != PyTokenTypes.AND) {
            return null;
        }
        PyExpression left = binaryExpression.getLeftExpression();
        if (left == null) {
            return null;
        }
        PyExpression right = binaryExpression.getRightExpression();
        if (right == null) {
            return null;
        }

        Ref<PyType> leftTypeRef = getType(left, context);
        Ref<PyType> rightTypeRef = getType(right, context);
        if (leftTypeRef == null && rightTypeRef == null) {
            return null;
        }

        PyType intersection = PyIntersectionType.intersectionOrTop(deref(leftTypeRef), deref(rightTypeRef));
        return intersection != null ? Ref.create(intersection) : null;
    }

    private static @Nullable Ref<PyType> getNoneType(PyExpression typeHint, PsiElement resolved) {
        if (typeHint instanceof PyNoneLiteralExpression ||
            typeHint instanceof PyReferenceExpression && PyNames.NONE.equals(typeHint.getText())
        ) {
            PyType noneType = PyBuiltinCache.getInstance(resolved).getNoneType();
            return Ref.create(noneType != null ? noneType : PyAnyType.getUnknown());
        }
        return null;
    }

    private static @Nullable PyType getCallableParameterListType(PsiElement resolved, Context context) {
        if (resolved instanceof PyListLiteralExpression listLiteralExpression) {
            List<PyCallableParameter> parameters = new ArrayList<>();
            for (PyExpression it : listLiteralExpression.getElements()) {
                parameters.add(PyCallableParameterImpl.nonPsi(PyTypeUtil.derefOrUnknown(getType(it, context))));
            }
            return new PyCallableParameterListTypeImpl(parameters);
        }
        return null;
    }

    private static @Nullable Ref<PyType> getNarrowedType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            Collection<String> names = resolveToQualifiedNames(
                subscriptionExpression.getOperand(),
                context.getTypeContext()
            );
            boolean isTypeIs = names.contains(TYPE_IS) || names.contains(TYPE_IS_EXT);
            boolean isTypeGuard = names.contains(TYPE_GUARD) || names.contains(TYPE_GUARD_EXT);
            if (isTypeIs || isTypeGuard) {
                List<@Nullable PyType> indexTypes = getIndexTypes(subscriptionExpression, context);
                if (indexTypes.size() == 1) {
                    PyNarrowedType narrowedType = PyNarrowedType.create(subscriptionExpression, isTypeIs, indexTypes.get(0));
                    if (narrowedType != null) {
                        return Ref.create(narrowedType);
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable Ref<PyType> getTypeFormType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            if (isTypeForm(resolveToQualifiedNames(subscriptionExpression.getOperand(), context.getTypeContext()), subscriptionExpression)) {
                PyType typeFormType = createTypeFormType(subscriptionExpression, subscriptionExpression.getIndexExpression(), context);
                return typeFormType != null ? Ref.create(typeFormType) : null;
            }
        }
        else {
            String qualifiedName = getQualifiedName(resolved);
            if (isTypeForm(qualifiedName != null ? List.of(qualifiedName) : List.of(), resolved)) {
                // Bare `TypeForm` is equivalent to `TypeForm[Any]`
                PyType typeFormType = createTypeFormType(resolved, null, context);
                return typeFormType != null ? Ref.create(typeFormType) : null;
            }
        }
        return null;
    }

    /**
     * Creates a {@link PyTypeFormType} whose represented type is the type denoted by {@code typeExpr} (a type expression, possibly a
     * string forward reference). A missing, `Any`, or unsupported {@code typeExpr} yields a form representing an arbitrary type.
     */
    private static @Nullable PyType createTypeFormType(PsiElement anchor, @Nullable PyExpression typeExpr, Context context) {
        PyType representedType =
            typeExpr != null && !resolvesToQualifiedNames(typeExpr, context.getTypeContext(), ANY)
                ? deref(getType(typeExpr, context))
                : PyAnyType.getAny();
        return PyTypeFormType.create(anchor, representedType);
    }

    // `typing.TypeForm` (PEP 747) only exists since Python 3.15; `typing_extensions.TypeForm` is a backport.
    private static boolean isTypeForm(Collection<String> names, PsiElement anchor) {
        if (names.contains(TYPE_FORM_EXT)) {
            return true;
        }
        return names.contains(TYPE_FORM) && LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON315);
    }

    private static @Nullable Ref<PyType> getSelfType(PsiElement resolved, PyExpression typeHint, Context context) {
        if (resolved instanceof PyQualifiedNameOwner qualifiedNameOwner &&
            (SELF.equals(qualifiedNameOwner.getQualifiedName()) ||
             SELF_EXT.equals(qualifiedNameOwner.getQualifiedName()))
        ) {
            PsiElement typeHintContext = getStubRetainedTypeHintContext(typeHint);

            PyClass containingClass = typeHintContext instanceof PyClass pyClass
                ? pyClass
                : PsiTreeUtil.getStubOrPsiParentOfType(typeHintContext, PyClass.class);
            if (containingClass == null) {
                return null;
            }

            if (!(containingClass.getType(context.getTypeContext()) instanceof PyClassType scopeClassType)) {
                return null;
            }

            return Ref.create(new PySelfType(scopeClassType).toInstance());
        }
        return null;
    }

    private static @Nullable Ref<PyType> getTypeFromParenthesizedExpression(PsiElement resolved, Context context) {
        if (resolved instanceof PyParenthesizedExpression parenthesizedExpression) {
            PyExpression containedExpression = PyPsiUtils.flattenParens(parenthesizedExpression);
            return containedExpression != null ? getType(containedExpression, context) : null;
        }
        return null;
    }

    private static @Nullable PyType anchorTypeParameter(PyExpression typeHint, @Nullable PyType type, Context context) {
        PyQualifiedNameOwner typeParamDefinitionFromStack = context.isTypeAliasStackEmpty() ? null : context.peekTypeAlias();
        assert typeParamDefinitionFromStack == null || typeParamDefinitionFromStack instanceof PyTargetExpression;
        PyTargetExpression targetExpr = (PyTargetExpression) typeParamDefinitionFromStack;
        if (type instanceof PyTypeVarTypeImpl typeVarType) {
            return typeVarType.withScopeOwner(getTypeParameterScope(typeVarType.getName(), typeHint, context)).withDeclarationElement(targetExpr);
        }
        if (type instanceof PyParamSpecType paramSpecType) {
            return paramSpecType.withScopeOwner(getTypeParameterScope(paramSpecType.getName(), typeHint, context))
                .withDeclarationElement(targetExpr);
        }
        if (type instanceof PyTypeVarTupleTypeImpl typeVarTupleType) {
            return typeVarTupleType.withScopeOwner(getTypeParameterScope(typeVarTupleType.getName(), typeHint, context))
                .withDeclarationElement(targetExpr);
        }
        return type;
    }

    private static @Nullable Ref<PyType> getClassObjectType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            PyExpression operand = subscriptionExpression.getOperand();
            if (resolvesToQualifiedNames(operand, context, TYPE, PyNames.FQN.TYPE)
                || (operand instanceof PyReferenceExpression operandReference
                    && QualifiedName.fromDottedString("builtins.type").equals(operandReference.asQualifiedName()))
            ) {
                PyExpression indexExpr = subscriptionExpression.getIndexExpression();
                if (indexExpr != null) {
                    if (resolvesToQualifiedNames(indexExpr, context.getTypeContext(), ANY)) {
                        PyType typeType = PyBuiltinCache.getInstance(subscriptionExpression).getTypeType();
                        return Ref.create(typeType != null ? typeType : PyAnyType.getUnknown());
                    }
                    return getAsClassObjectType(indexExpr, context);
                }
                // Map Type[Something] with unsupported type parameter to Any, instead of a generic type for the class "type"
                return Ref.create(PyAnyType.getUnknown());
            }
        }
        else if (TYPE.equals(getQualifiedName(resolved))) {
            PyType typeType = PyBuiltinCache.getInstance(resolved).getTypeType();
            return Ref.create(typeType != null ? typeType : PyAnyType.getUnknown());
        }
        return null;
    }

    private static Ref<PyType> getAsClassObjectType(PyExpression expression, Context context) {
        if (context.getTypeRepresentationMode() && expression instanceof PyReferenceExpression referenceExpression) {
            // handle "builtins.type[Generic]" until pyrefly is updated to return fully qualified version
            PyType type = new PyTypingTypeProvider().getReferenceExpressionType(referenceExpression, context);
            if (type != null) {
                return Ref.create(type);
            }

            // pyrefly currently sends special forms as "builtins.type[Literal]"
            String name = referenceExpression.getName();
            PyType specialFormType;
            if ("Callable".equals(name)) {
                specialFormType = createTypingCallableType(referenceExpression);
            }
            else if ("Literal".equals(name)) {
                PyClass specialFormClass = PyPsiFacade.getInstance(referenceExpression.getProject())
                    .createClassByQName(SPECIAL_FORM, referenceExpression);
                specialFormType = specialFormClass != null ? new PyClassTypeImpl(specialFormClass, false) : null;
            }
            else if ("TypedDict".equals(name)) {
                specialFormType = new PyCustomType(TYPED_DICT, null, false, true, PyBuiltinCache.getInstance(referenceExpression).getDictType());
            }
            else if ("Protocol".equals(name)) {
                specialFormType = createTypingProtocolType(referenceExpression);
            }
            else if ("Generic".equals(name)) {
                specialFormType = createTypingGenericType(referenceExpression);
            }
            else {
                specialFormType = null;
            }
            if (specialFormType != null) {
                return Ref.create(specialFormType);
            }
        }
        PyType type = deref(getType(expression, context));
        if (type instanceof PyClassType classType && !classType.isDefinition()) {
            return Ref.create(classType.toClass());
        }
        if (type instanceof PyTypeVarType typeVar && !typeVar.isDefinition()) {
            return Ref.create(typeVar.toClass());
        }
        if (type instanceof PySelfType selfType) {
            return Ref.create(selfType.toClass());
        }
        // Represent Type[Union[str, int]] internally as Union[Type[str], Type[int]]
        if (type instanceof PyUnionType unionType &&
            unionType.getMembers().stream().allMatch(it -> it instanceof PyClassType classType && !classType.isDefinition())
        ) {
            return Ref.create(unionType.map(it -> ((PyClassType) it).toClass()));
        }
        return Ref.create(PyAnyType.getUnknown());
    }

    private static @Nullable Ref<PyType> getAnyType(PsiElement element, Context context) {
        if (ANY.equals(getQualifiedName(element))) {
            return Ref.create(PyAnyType.getAny());
        }
        if (context.getTypeRepresentationMode() && ANY.equals(element.getText())) {
            return Ref.create(PyAnyType.getAny());
        }
        return null;
    }

    private static @Nullable Ref<PyType> getClassType(PyExpression typeHint,
                                                      PsiElement element,
                                                      boolean parameterizeTopLevel,
                                                      Context context) {
        if (typeHint instanceof PyReferenceExpression && element instanceof PyTypedElement typedElement) {
            PyType type = context.getTypeContext().getType(typedElement);
            if (type instanceof PyClassLikeType classLikeType) {
                if (classLikeType.isDefinition()) {
                    // If we're interpreting a type hint like "MyGeneric" that is not followed by a list of type arguments (e.g., MyGeneric[int]),
                    // we want to parameterize it with its type parameters defaults already here.
                    // We need this check for the type argument list because getParameterizedType() relies on getClassType() for
                    // getting the type corresponding to the subscription expression operand.
                    PsiElement stubRetainedContext = getStubRetainedTypeHintContext(typeHint);
                    boolean isTopLevelExpression = PsiTreeUtil.skipParentsOfType(typeHint, PyParenthesizedExpression.class) instanceof PyStatement;
                    boolean shouldParameterize = (!isTopLevelExpression || parameterizeTopLevel); // old style alias
                    if (
                        shouldParameterize &&
                        classLikeType instanceof PyClassType classType
                        && !(stubRetainedContext instanceof PyClass ||
                             PsiTreeUtil.getStubOrPsiParentOfType(stubRetainedContext, ScopeOwner.class) instanceof PyClass)
                        && !(typeHint.getParent() instanceof PySubscriptionExpression parentSubscription && parentSubscription.getOperand() == typeHint) &&
                        isGeneric(classType, context.getTypeContext())
                    ) {
                        PyClassType parameterized = parameterizeType(classType, context.getTypeContext());
                        if (parameterized != null) {
                            return Ref.create(parameterized.toInstance());
                        }
                    }
                    PyType instanceType = PyNumericTowerUtil.enrich(classLikeType.toInstance());
                    return Ref.create(instanceType);
                }
            }
        }
        return null;
    }

    private static @Nullable Ref<PyType> getOptionalType(PsiElement element, Context context) {
        if (element instanceof PySubscriptionExpression subscriptionExpression) {
            if (resolvesToQualifiedNames(subscriptionExpression.getOperand(), context.getTypeContext(), OPTIONAL)) {
                PyExpression indexExpr = PyPsiUtils.flattenParens(subscriptionExpression.getIndexExpression());
                PyExpression argExpr;
                if (indexExpr instanceof PyTupleExpression tupleExpression) {
                    PyExpression[] elements = tupleExpression.getElements();
                    argExpr = elements.length == 1 ? elements[0] : tupleExpression;
                }
                else {
                    argExpr = indexExpr;
                }

                PyType argType = PyAnyType.getUnknown();
                if (argExpr != null) {
                    argType = PyTypeUtil.derefOrUnknown(getType(argExpr, context));
                }
                PyType noneType = PyBuiltinCache.getInstance(subscriptionExpression).getNoneType();
                return Ref.create(PyUnionType.unionOrUnknown(argType, noneType != null ? noneType : PyAnyType.getUnknown()));
            }
        }
        return null;
    }

    private static @Nullable Ref<PyType> getLiteralStringType(PsiElement resolved, Context context) {
        if (resolved instanceof PyTargetExpression targetExpression) {
            if (resolvesToQualifiedNames(targetExpression, context.getTypeContext(), LITERALSTRING, LITERALSTRING_EXT)) {
                PyType literalStringType = PyLiteralStringType.create(targetExpression);
                return Ref.create(literalStringType != null ? literalStringType : PyAnyType.getUnknown());
            }
        }

        return null;
    }

    private static @Nullable Ref<PyType> getLiteralType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            if (resolvesToQualifiedNames(subscriptionExpression.getOperand(), context, LITERAL, LITERAL_EXT)) {
                PyExpression indexExpression = subscriptionExpression.getIndexExpression();
                if (indexExpression == null) {
                    return null;
                }
                PyType literalType =
                    PyLiteralType.fromLiteralParameter(indexExpression, context.getTypeContext(), context.getTypeRepresentationMode());
                return literalType != null ? Ref.create(literalType) : null;
            }
        }

        return null;
    }

    private static @Nullable Ref<PyType> getAnnotatedType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            PyExpression operand = subscriptionExpression.getOperand();
            if (resolvesToQualifiedNames(
                operand,
                context.getTypeContext(),
                ANNOTATED,
                ANNOTATED_EXT
            )
            ) {
                PyExpression indexExpr = subscriptionExpression.getIndexExpression();
                PyExpression type = indexExpr instanceof PyTupleExpression tupleExpression ? tupleExpression.getElements()[0] : indexExpr;
                if (type != null) {
                    return getType(type, context);
                }
            }
        }

        return null;
    }

    private static @Nullable Ref<PyType> getTypedDictSpecialItemType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            PyExpression operand = subscriptionExpression.getOperand();
            if (resolvesToQualifiedNames(
                operand,
                context.getTypeContext(),
                REQUIRED, REQUIRED_EXT,
                NOT_REQUIRED, NOT_REQUIRED_EXT,
                READONLY, READONLY_EXT
            )
            ) {
                PyExpression indexExpr = subscriptionExpression.getIndexExpression();
                PyExpression type = indexExpr instanceof PyTupleExpression tupleExpression ? tupleExpression.getElements()[0] : indexExpr;
                if (type != null) {
                    return getType(type, context);
                }
            }
        }

        return null;
    }

    private static @Nullable Ref<PyType> unwrapTypeModifier(PsiElement resolved, Context context, @Nullable String... type) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            if (resolvesToQualifiedNames(subscriptionExpression.getOperand(), context.getTypeContext(), type)) {
                PyExpression indexExpr = subscriptionExpression.getIndexExpression();
                if (indexExpr != null) {
                    return getType(indexExpr, context);
                }
            }
        }

        return null;
    }

    private static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean typeHintedWithName(
        T owner,
        TypeEvalContext context,
        @Nullable String... names
    ) {
        for (String it : names) {
            if (resolveTypeHintsToQualifiedNames(owner, context).contains(it)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends PyTypeCommentOwner & PyAnnotationOwner> Collection<String> resolveTypeHintsToQualifiedNames(
        T owner,
        TypeEvalContext context
    ) {
        PyExpression annotation = getAnnotationValue(owner, context);
        if (annotation instanceof PyStringLiteralExpression stringLiteralExpression) {
            String annotationText = stringLiteralExpression.getStringValue();
            annotation = PyUtil.createExpressionFromFragment(annotationText, owner);
            if (annotation == null) {
                return new ArrayList<>();
            }
        }

        if (annotation instanceof PySubscriptionExpression subscriptionExpression) {
            return resolveToQualifiedNames(subscriptionExpression.getOperand(), context);
        }
        else if (annotation instanceof PyReferenceExpression) {
            return resolveToQualifiedNames(annotation, context);
        }

        String typeCommentValue = owner.getTypeCommentAnnotation();
        PyExpression typeComment = typeCommentValue == null ? null : PyUtil.createExpressionFromFragment(typeCommentValue, owner);
        if (typeComment instanceof PySubscriptionExpression subscriptionExpression) {
            return resolveToQualifiedNames(subscriptionExpression.getOperand(), context);
        }
        else if (typeComment instanceof PyReferenceExpression) {
            return resolveToQualifiedNames(typeComment, context);
        }

        return new ArrayList<>();
    }

    public static boolean isFinal(PyDecoratable decoratable, TypeEvalContext context) {
        return PyKnownDecoratorUtil.knownDecoratorsOf(decoratable, context)
            .containsAny(PyKnownDecorator.TYPING_FINAL, PyKnownDecorator.TYPING_FINAL_EXT);
    }

    /**
     * Returns true if and only if {@code cls} accepts no subclass. A class with the `@final` decorator
     * accepts none. An enum class with one member or more also accepts none.
     */
    public static boolean isFinalClass(PyClass cls, TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(cls, context, it -> {
            return isFinal((PyDecoratable) cls, context) || PyStdlibTypeProvider.isFinalEnum(cls, context);
        });
    }

    public static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean isFinal(T owner, TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(owner, context, it -> {
            return typeHintedWithName(owner, context, FINAL, FINAL_EXT);
        });
    }

    public static <T extends PyTypeCommentOwner & PyAnnotationOwner> boolean isReadOnly(T owner, TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(owner, context, it -> {
            return typeHintedWithName(owner, context, READONLY, READONLY_EXT);
        });
    }

    public static <T extends PyAnnotationOwner & PyTypeCommentOwner> boolean isClassVar(T owner, TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(owner, context, it -> {
            return typeHintedWithName(owner, context, CLASS_VAR);
        });
    }

    private static boolean resolvesToQualifiedNames(PyExpression expression, Context context, @Nullable String... names) {
        if (!context.getTypeRepresentationMode()) {
            return resolvesToQualifiedNames(expression, context.getTypeContext(), names);
        }
        if (!(expression instanceof PyReferenceExpression referenceExpression)) {
            return false;
        }
        PyExpression qualifier = referenceExpression.getQualifier();
        if (qualifier == null) {
            return false;
        }
        String qName = qualifier.getName() + "." + referenceExpression.getName();
        for (String it : names) {
            if (Objects.equals(it, qName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resolvesToQualifiedNames(PyExpression expression, TypeEvalContext context, @Nullable String... names) {
        Collection<String> qualifiedNames = resolveToQualifiedNames(expression, context);
        for (String it : names) {
            if (qualifiedNames.contains(it)) {
                return true;
            }
        }
        return false;
    }

    // @ApiStatus.Internal
    public static @Nullable PyExpression getAnnotationValue(PyAnnotationOwner owner, TypeEvalContext context) {
        if (context.maySwitchToAST(owner)) {
            PyAnnotation annotation = owner.getAnnotation();
            if (annotation != null) {
                return annotation.getValue();
            }
        }
        else {
            String annotationText = owner.getAnnotationValue();
            if (annotationText != null) {
                return PyUtil.createExpressionFromFragment(annotationText, owner);
            }
        }
        return null;
    }

    public static @Nullable Ref<PyType> getStringBasedType(
        String contents,
        PsiElement anchor,
        TypeEvalContext context
    ) {
        return staticWithCustomContext(context, it -> getStringBasedType(contents, anchor, it));
    }

    private static @Nullable Ref<PyType> getStringBasedType(String contents, PsiElement anchor, Context context) {
        return RecursionManager.doPreventingRecursion(Pair.create(anchor, contents), true, () -> {
            PyExpression expr = PyUtil.createExpressionFromFragment(contents, anchor);
            return expr != null ? getType(expr, context) : null;
        });
    }

    private static @Nullable PyType getStringLiteralType(PsiElement element, Context context) {
        if (element instanceof PyStringLiteralExpression stringLiteralExpression) {
            String contents = stringLiteralExpression.getStringValue();
            // A multiline string literal can contain a type expression unparsable without parentheses
            return deref(
                getStringBasedType(
                    contents.contains("\n") ? "(" + contents + ")" : contents,
                    element,
                    context
                )
            );
        }
        return null;
    }

    private static @Nullable Ref<PyType> getVariableTypeCommentType(
        String contents,
        PsiElement element,
        Context context
    ) {
        PyExpression expr = PyPsiUtils.flattenParens(PyUtil.createExpressionFromFragment(contents, element));
        if (expr != null) {
            if (element instanceof PyTargetExpression targetExpression && expr instanceof PyTupleExpression) {
                // Such syntax is specific to "# type:" comments, unpacking in type hints is not allowed anywhere else
                // XXX: Switches stub to AST
                PyExpression topmostTarget = findTopmostTarget(targetExpression);
                if (topmostTarget != null) {
                    Map<PyTargetExpression, PyExpression> targetToExpr = mapTargetsToAnnotations(topmostTarget, expr);
                    PyExpression typeExpr = targetToExpr.get(targetExpression);
                    if (typeExpr != null) {
                        return getType(typeExpr, context);
                    }
                }
            }
            else {
                return getType(expr, context);
            }
        }
        return null;
    }

    private static @Nullable PyExpression findTopmostTarget(PyTargetExpression target) {
        PsiElement validTargetParent = PsiTreeUtil.getParentOfType(
            target,
            PyForPart.class,
            PyWithItem.class,
            PyAssignmentStatement.class
        );
        if (validTargetParent == null) {
            return null;
        }
        PyExpression topmostTarget = PsiTreeUtil.findPrevParent(validTargetParent, target) instanceof PyExpression expression ? expression : null;
        if (validTargetParent instanceof PyForPart forPart && topmostTarget != forPart.getTarget()) {
            return null;
        }
        if (validTargetParent instanceof PyWithItem withItem && topmostTarget != withItem.getTarget()) {
            return null;
        }
        if (validTargetParent instanceof PyAssignmentStatement assignmentStatement &&
            Arrays.asList(assignmentStatement.getRawTargets()).indexOf(topmostTarget) < 0
        ) {
            return null;
        }
        return topmostTarget;
    }

    public static Map<PyTargetExpression, PyExpression> mapTargetsToAnnotations(
        PyExpression targetExpr,
        PyExpression typeExpr
    ) {
        PyExpression targetsNoParen = PyPsiUtils.flattenParens(targetExpr);
        PyExpression typesNoParen = PyPsiUtils.flattenParens(typeExpr);
        if (targetsNoParen == null || typesNoParen == null) {
            return new HashMap<>();
        }
        if (targetsNoParen instanceof PySequenceExpression targetSequence && typesNoParen instanceof PySequenceExpression typeSequence) {
            Ref<Map<PyTargetExpression, PyExpression>> result = Ref.create(new LinkedHashMap<>());
            mapTargetsToExpressions(targetSequence, typeSequence, result);
            return result.isNull() ? new HashMap<>()
                : Collections.unmodifiableMap(
                Objects.requireNonNull(result.get())
            );
        }
        else if (targetsNoParen instanceof PyTargetExpression targetExpression && !(typesNoParen instanceof PySequenceExpression)) {
            return ImmutableMap.of(targetExpression, typesNoParen);
        }
        return Collections.emptyMap();
    }

    private static void mapTargetsToExpressions(
        PySequenceExpression targetSequence,
        PySequenceExpression valueSequence,
        Ref<Map<PyTargetExpression, PyExpression>> result
    ) {
        PyExpression[] targets = targetSequence.getElements();
        PyExpression[] values = valueSequence.getElements();

        if (targets.length != values.length) {
            result.set(null);
            return;
        }

        for (int i = 0; i < targets.length; i++) {
            PyExpression target = PyPsiUtils.flattenParens(targets[i]);
            PyExpression value = PyPsiUtils.flattenParens(values[i]);

            if (target == null || value == null) {
                result.set(null);
                return;
            }

            if (target instanceof PySequenceExpression targetSubSequence && value instanceof PySequenceExpression valueSubSequence) {
                mapTargetsToExpressions(targetSubSequence, valueSubSequence, result);
                if (result.isNull()) {
                    return;
                }
            }
            else if (target instanceof PyTargetExpression targetExpression && !(value instanceof PySequenceExpression)) {
                Map<PyTargetExpression, PyExpression> map = Objects.requireNonNull(result.get());
                map.put(targetExpression, value);
            }
            else {
                result.set(null);
                return;
            }
        }
    }

    private static @Nullable PyType getCallableType(PsiElement resolved, Context context) {
        if (resolved instanceof PySubscriptionExpression subscriptionExpression) {
            if (resolvesToQualifiedNames(
                subscriptionExpression.getOperand(),
                context.getTypeContext(),
                CALLABLE,
                CALLABLE_EXT
            )
            ) {
                PyType returnType = PyAnyType.getUnknown();

                PyExpression indexExpr = subscriptionExpression.getIndexExpression();
                if (indexExpr instanceof PyTupleExpression tupleExpression) {
                    PyExpression[] elements = tupleExpression.getElements();
                    if (elements.length >= 2) {
                        PyExpression parametersExpr = elements[0];
                        PyExpression returnTypeExpr = elements[1];
                        returnType = PyTypeUtil.derefOrUnknown(getType(returnTypeExpr, context));
                        if (returnType instanceof PyVariadicType) {
                            returnType = PyAnyType.getUnknown();
                        }
                        if (parametersExpr instanceof PyEllipsisLiteralExpression) {
                            return PyCallableTypeImpl.withUnknownParameters(returnType);
                        }
                        PyType parametersType = deref(getType(parametersExpr, context));
                        if (parametersType instanceof PyCallableParameterListType parameterListType) {
                            return new PyCallableTypeImpl(parameterListType.getParameters(), returnType);
                        }
                        if (parametersType instanceof PyCallableParameterVariadicType parameterVariadicType) {
                            return new PyCallableTypeImpl(parameterVariadicType, returnType);
                        }
                    }
                }
                return PyCallableTypeImpl.withUnknownParameters(returnType);
            }
        }
        else if (resolved instanceof PyTargetExpression targetExpression) {
            if (resolvesToQualifiedNames(
                targetExpression,
                context.getTypeContext(),
                CALLABLE,
                CALLABLE_EXT
            )
            ) {
                return PyCallableTypeImpl.withUnknownParameters(PyAnyType.getUnknown());
            }
        }
        return null;
    }

    private static @Nullable PyType getNeverType(PsiElement element) {
        String qName = getQualifiedName(element);
        if (qName == null) {
            return null;
        }
        if (List.of(NEVER, NEVER_EXT).contains(qName)) {
            return PyNeverType.NEVER;
        }
        if (List.of(NO_RETURN, NO_RETURN_EXT).contains(qName)) {
            return PyNeverType.NO_RETURN;
        }
        return null;
    }

    private static @Nullable Ref<PyType> getUnionType(PsiElement element, Context context) {
        if (element instanceof PySubscriptionExpression subscriptionExpression) {
            if (resolvesToQualifiedNames(subscriptionExpression.getOperand(), context.getTypeContext(), UNION)) {
                PyType union = PyUnionType.unionOrNever(getIndexTypes(subscriptionExpression, context));
                return union != null ? Ref.create(union) : null;
            }
        }
        else if (element instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
            PyExpression left = binaryExpression.getLeftExpression();
            if (left == null) {
                return null;
            }
            PyExpression right = binaryExpression.getRightExpression();
            if (right == null) {
                return null;
            }

            if (isOperatorCallOperand(left, "__or__", context) ||
                isOperatorCallOperand(right, "__ror__", context)) {
                return null;
            }

            Ref<PyType> leftTypeRef = getType(left, context);
            Ref<PyType> rightTypeRef = getType(right, context);
            if (leftTypeRef == null && rightTypeRef == null) {
                return null;
            }

            PyType union = PyUnionType.unionOrUnknown(PyTypeUtil.derefOrUnknown(leftTypeRef), PyTypeUtil.derefOrUnknown(rightTypeRef));
            return union != null ? Ref.create(union) : null;
        }
        return null;
    }

    private static @Nullable PyType getConcatenateType(PsiElement element, Context context) {
        if (!(element instanceof PySubscriptionExpression subscriptionExpression)) {
            return null;
        }
        if (!resolvesToQualifiedNames(subscriptionExpression.getOperand(), context.getTypeContext(), CONCATENATE, CONCATENATE_EXT)) {
            return null;
        }
        if (!(subscriptionExpression.getIndexExpression() instanceof PyTupleExpression tupleExpression)) {
            return null;
        }

        List<PyExpression> arguments = List.of(tupleExpression.getElements());
        if (arguments.size() < 2) {
            return null;
        }

        List<PyExpression> prefixTypeExprs = arguments.subList(0, arguments.size() - 1);
        List<@Nullable PyType> prefixTypes = new ArrayList<>();
        for (PyExpression it : prefixTypeExprs) {
            prefixTypes.add(PyTypeUtil.derefOrUnknown(getType(it, context.getTypeContext())));
        }

        PyExpression lastTypeExpr = arguments.get(arguments.size() - 1);
        PyParamSpecType paramSpecType;
        if (lastTypeExpr instanceof PyEllipsisLiteralExpression) {
            paramSpecType = null;
        }
        else {
            if (!(deref(getType(lastTypeExpr, context.getTypeContext())) instanceof PyParamSpecType lastParamSpecType)) {
                return null;
            }
            paramSpecType = lastParamSpecType;
        }
        return new PyConcatenateType(prefixTypes, paramSpecType);
    }

    private static @Nullable PyTypeParameterType getTypeParameterTypeFromDeclaration(PsiElement element, Context context) {
        if (!(element instanceof PyCallExpression callExpression)) {
            return null;
        }
        PyAstTypeParameter.Kind typeParameterKind = getTypeParameterKindFromDeclaration(
            callExpression,
            context.getTypeContext()
        );
        if (typeParameterKind == null) {
            return null;
        }
        PyExpression[] arguments = callExpression.getArguments();
        if (!(arguments.length > 0 && arguments[0] instanceof PyStringLiteralExpression nameArgument)) {
            return null;
        }
        String name = nameArgument.getStringValue();
        PyExpression defaultExpression = callExpression.getKeywordArgument("default");
        PyExpression boundExpression = callExpression.getKeywordArgument("bound");
        PyType boundType = boundExpression != null ? deref(getType(boundExpression, context)) : null;
        PyType bound = boundType != null ? boundType : PyAnyType.getUnknown();
        Ref<PyType> defaultType = defaultExpression != null ? getType(defaultExpression, context) : null;
        PyVariance variance = getTypeVarVarianceFromDeclaration(callExpression);
        switch (typeParameterKind) {
            case TypeVar: {
                // TypeVar __init__ parameters:
                // (name, *constraints, bound = None, contravariant = False, covariant = False, infer_variance = False, default = ...)
                List<@Nullable PyType> constraints = new ArrayList<>();
                for (int i = 1; i < arguments.length; i++) {
                    PyExpression it = arguments[i];
                    if (it instanceof PyKeywordArgument) {
                        break;
                    }
                    constraints.add(PyTypeUtil.derefOrUnknown(getType(it, context)));
                }
                PyQualifiedNameOwner declarationElement = null;
                if (callExpression.getParent() instanceof PyAssignmentStatement assignStmt) {
                    for (Pair<PyExpression, PyExpression> pair : assignStmt.getTargetsToValuesMapping()) {
                        if (Objects.equals(pair.getSecond(), callExpression)) {
                            declarationElement = pair.getFirst() instanceof PyQualifiedNameOwner owner ? owner : null;
                            break;
                        }
                    }
                }
                return new PyTypeVarTypeImpl(name, constraints, bound, defaultType, variance).withDeclarationElement(declarationElement);
            }

            case TypeVarTuple: {
                Ref<PyPositionalVariadicType> variadicDefaultType =
                    deref(defaultType) instanceof PyPositionalVariadicType positionalVariadicType ? Ref.create(positionalVariadicType) : null;
                return new PyTypeVarTupleTypeImpl(name)
                    .withBound(bound)
                    .withDefaultType(variadicDefaultType)
                    .withVariance(variance);
            }

            case ParamSpec: {
                return new PyParamSpecType(name)
                    .withBound(bound)
                    .withDefaultType(getParamSpecDefaultType(defaultExpression, defaultType))
                    .withVariance(variance);
            }
        }
        return null;
    }

    @SuppressWarnings("ConstantConditions") // caused by systematical if conditions
    private static PyVariance getTypeVarVarianceFromDeclaration(PyCallExpression assignedCall) {
        boolean covariant = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("covariant"), false);
        boolean contravariant = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("contravariant"), false);
        boolean inferVariance = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("infer_variance"), false);

        if (covariant && !contravariant) {
            return PyVariance.COVARIANT;
        }
        else if (contravariant && !covariant) {
            return PyVariance.CONTRAVARIANT;
        }
        else if (contravariant && covariant) {
            // Note that Python does not officially support bivariance. Change this to invariant if necessary.
            return PyVariance.BIVARIANT;
        }
        else if (inferVariance) {
            return PyVariance.INFER_VARIANCE;
        }
        else {
            return PyVariance.INVARIANT;
        }
    }

    // @ApiStatus.Internal
    public static PyAstTypeParameter.@Nullable Kind getTypeParameterKindFromDeclaration(
        PyCallExpression callExpression,
        TypeEvalContext context
    ) {
        PyExpression callee = callExpression.getCallee();
        if (callee != null) {
            Collection<String> calleeQNames = resolveToQualifiedNames(callee, context);
            if (calleeQNames.contains(TYPE_VAR_TUPLE) || calleeQNames.contains(TYPE_VAR_TUPLE_EXT)) {
                return PyAstTypeParameter.Kind.TypeVarTuple;
            }
            if (calleeQNames.contains(TYPE_VAR) || calleeQNames.contains(TYPE_VAR_EXT)) {
                return PyAstTypeParameter.Kind.TypeVar;
            }
            if (calleeQNames.contains(PARAM_SPEC) || calleeQNames.contains(PARAM_SPEC_EXT)) {
                return PyAstTypeParameter.Kind.ParamSpec;
            }
        }
        return null;
    }

    // @ApiStatus.Internal
    public static @Nullable PyTypeParameterType getTypeParameterTypeFromTypeParameter(
        PyTypeParameter typeParameter,
        TypeEvalContext context
    ) {
        return staticWithCustomContext(context, it -> getTypeParameterTypeFromTypeParameter((PsiElement) typeParameter, it));
    }

    private static @Nullable PyTypeParameterType getTypeParameterTypeFromTypeParameter(
        PsiElement element,
        Context context
    ) {
        if (!(element instanceof PyTypeParameter typeParameter)) {
            return null;
        }
        String name = typeParameter.getName();
        if (name == null) {
            return null;
        }

        ScopeOwner typeParameterOwner = ScopeUtil.getScopeOwner(typeParameter);
        PyQualifiedNameOwner scopeOwner = typeParameterOwner instanceof PyQualifiedNameOwner owner ? owner : null;

        String defaultExpressionText = typeParameter.getDefaultExpressionText();
        PyExpression defaultExpression =
            defaultExpressionText != null ? PyUtil.createExpressionFromFragment(defaultExpressionText, typeParameter) : null;
        Ref<PyType> defaultType;
        if (defaultExpression != null) {
            PyExpression defaultExprWithoutParens = PyPsiUtils.flattenParens(defaultExpression);
            if (defaultExprWithoutParens != null) {
                defaultType = getTypePreventingRecursion(defaultExprWithoutParens, context);
            }
            else {
                defaultType = Ref.create(PyAnyType.getUnknown());
            }
        }
        else {
            defaultType = null;
        }

        String boundExpressionText = typeParameter.getBoundExpressionText();
        PyExpression boundExpression = boundExpressionText != null
            ? PyPsiUtils.flattenParens(PyUtil.createExpressionFromFragment(boundExpressionText, typeParameter))
            : null;
        PyType boundType;
        List<@Nullable PyType> constraints;
        if (boundExpression == null) {
            boundType = PyAnyType.getUnknown();
            constraints = Collections.emptyList();
        }
        else if (boundExpression instanceof PyTupleExpression tupleExpression) {
            boundType = PyAnyType.getUnknown();
            constraints = new ArrayList<>();
            for (PyExpression it : tupleExpression.getElements()) {
                constraints.add(PyTypeUtil.derefOrUnknown(getTypePreventingRecursion(it, context)));
            }
        }
        else {
            boundType = PyTypeUtil.derefOrUnknown(getTypePreventingRecursion(boundExpression, context));
            constraints = Collections.emptyList();
        }

        PyVariance variance =
            scopeOwner instanceof PyFunction ? PyVariance.INVARIANT : PyVariance.INFER_VARIANCE;

        PyQualifiedNameOwner declarationElement = typeParameter;

        switch (typeParameter.getKind()) {
            case TypeVar: {
                return new PyTypeVarTypeImpl(name, constraints, boundType, defaultType, variance)
                    .withScopeOwner(scopeOwner)
                    .withDeclarationElement(declarationElement);
            }

            case TypeVarTuple: {
                Ref<PyPositionalVariadicType> variadicDefaultType =
                    defaultType != null && defaultType.get() instanceof PyPositionalVariadicType positionalVariadicType
                        ? Ref.create(positionalVariadicType)
                        : null;
                return new PyTypeVarTupleTypeImpl(name)
                    .withScopeOwner(scopeOwner)
                    .withBound(boundType)
                    .withDefaultType(variadicDefaultType)
                    .withVariance(variance)
                    .withDeclarationElement(declarationElement);
            }

            case ParamSpec: {
                return new PyParamSpecType(name)
                    .withScopeOwner(scopeOwner)
                    .withBound(boundType)
                    .withDefaultType(getParamSpecDefaultType(defaultExpression, defaultType))
                    .withVariance(variance)
                    .withDeclarationElement(declarationElement);
            }
        }
        return null;
    }

    /**
     * Returns {@link PyAnyType#getUnknown()} for the `...` default of a ParamSpec.
     * An explicit `...` type argument of a ParamSpec gets the same type.
     */
    private static @Nullable Ref<PyType> getParamSpecDefaultType(@Nullable PyExpression defaultExpression,
                                                                 @Nullable Ref<PyType> defaultType) {
        if (PyPsiUtils.flattenParens(defaultExpression) instanceof PyEllipsisLiteralExpression) {
            return Ref.create(PyAnyType.getUnknown());
        }
        return deref(defaultType) instanceof PyCallableParameterVariadicType variadicType ? Ref.create(variadicType) : null;
    }

    private static @Nullable Ref<PyType> getTypePreventingRecursion(PyExpression expression, Context context) {
        return RecursionManager.doPreventingRecursion(expression, false, () -> getType(expression, context));
    }

    // See https://peps.python.org/pep-0484/#scoping-rules-for-type-variables
    private static @Nullable PyQualifiedNameOwner getTypeParameterScope(
        String name,
        PyExpression typeHint,
        Context context
    ) {
        if (!context.isComputeTypeParameterScopeEnabled()) {
            return null;
        }

        PsiElement typeHintContext = getStubRetainedTypeHintContext(typeHint);
        List<PyQualifiedNameOwner> typeParamOwnerCandidates = new ArrayList<>();
        for (PsiElement it = typeHintContext; it != null; it = PsiTreeUtil.getStubOrPsiParentOfType(it, ScopeOwner.class)) {
            if (it instanceof PyFunction || it instanceof PyClass) {
                typeParamOwnerCandidates.add((PyQualifiedNameOwner) it);
            }
        }

        PyQualifiedNameOwner closestOwner = typeParamOwnerCandidates.isEmpty() ? null : typeParamOwnerCandidates.get(0);
        // A class with a type parameter list does not bind traditional type variables, so an outer scope can bind them.
        boolean isNewStyleClass = closestOwner instanceof PyClass closestClass && closestClass.getTypeParameterList() != null;
        if (closestOwner instanceof PyFunction || isNewStyleClass) {
            PyTypeParameterType typeParameterType = null;
            for (PyQualifiedNameOwner it : typeParamOwnerCandidates.subList(1, typeParamOwnerCandidates.size())) {
                typeParameterType = findSameTypeParameterInDefinition(it, name, context);
                if (typeParameterType != null) {
                    break;
                }
            }
            if (typeParameterType != null) {
                return typeParameterType.getScopeOwner();
            }
        }
        // In `def f(x: Unresolved[T])`, the annotation has no type, so the signature does not show `T`. The function still binds it.
        if (closestOwner instanceof PyFunction closestFunction &&
            isArgumentOfUnresolvedGenericInSignature(typeHintContext, closestFunction, context.getTypeContext())) {
            return closestOwner;
        }
        if (closestOwner != null && !isNewStyleClass) {
            boolean prevComputeTypeParameterScope = context.setComputeTypeParameterScopeEnabled(false);
            try {
                if (findSameTypeParameterInDefinition(closestOwner, name, context) != null) {
                    return closestOwner;
                }
            }
            finally {
                context.setComputeTypeParameterScopeEnabled(prevComputeTypeParameterScope);
            }
            // A class body can declare an old-style type alias with its own type parameters.
            if (!(closestOwner instanceof PyClass)) {
                return null;
            }
        }

        // old-style type aliases of form `ListOf = list[T]` or `ListOf: TypeAlias = list[T]`
        // Without the AST, the value of the alias is a fragment, and the target of the alias is its context.
        if (typeHintContext instanceof PyTargetExpression typeHintTarget) {
            PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(typeHintTarget);
            if (assignedValue != null && PsiTreeUtil.isAncestor(assignedValue, typeHint, false) && isTypeAliasValue(assignedValue, context)) {
                return typeHintTarget;
            }
            return null;
        }
        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(
            typeHintContext,
            PyAssignmentStatement.class,
            false,
            PyStatement.class
        );
        if (assignment != null) {
            PyExpression assignedValue = PyPsiUtils.flattenParens(assignment.getAssignedValue());
            if (PsiTreeUtil.isAncestor(assignedValue, typeHintContext, false)) {
                PyExpression target = PyPsiUtils.flattenParens(assignment.getLeftHandSideExpression());
                if (target instanceof PyTargetExpression targetExpression && isTypeAliasValue(Objects.requireNonNull(assignedValue), context)) {
                    return targetExpression;
                }
            }
        }

        return null;
    }

    private static boolean isTypeAliasValue(PyExpression assignedValue, Context context) {
        boolean isTypeParamDeclaration = assignedValue instanceof PyCallExpression callExpression &&
                                         getTypeParameterKindFromDeclaration(callExpression, context.getTypeContext()) != null;
        return !isTypeParamDeclaration && PyTypingAliasStubType.looksLikeTypeHint(assignedValue);
    }

    private static boolean isArgumentOfUnresolvedGenericInSignature(PsiElement typeHint, PyFunction function, TypeEvalContext context) {
        if (!context.maySwitchToAST(function)) {
            return false;
        }
        PyAnnotation annotation = PsiTreeUtil.getParentOfType(typeHint, PyAnnotation.class, true, ScopeOwner.class);
        if (annotation == null) {
            return false;
        }
        PsiElement annotated = annotation.getParent();
        if (annotated != function && !(annotated instanceof PyNamedParameter && annotated.getParent() != null && annotated.getParent().getParent() == function)) {
            return false;
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        for (PsiElement it = typeHint.getParent(); it != null && it != annotation; it = it.getParent()) {
            if (it instanceof PySubscriptionExpression subscriptionExpression &&
                subscriptionExpression.getOperand() instanceof PyReferenceExpression operand) {
                List<PsiElement> declarations = PyResolveUtil.multiResolveDeclaration(operand.getReference(resolveContext), resolveContext);
                if (declarations.stream().allMatch(declaration -> declaration instanceof PyImportElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static @Nullable PyTypeParameterType findSameTypeParameterInDefinition(
        PyQualifiedNameOwner owner,
        String name,
        Context context
    ) {
        // At this moment, the definition of the TypeVar should be the type alias at the top of the stack.
        if (context.isTypeAliasStackEmpty()) {
            return null;
        }
        // The check evaluates the signature of the owner with an empty alias stack.
        // Otherwise, the protection against recursive aliases hides the type parameters of the signature.
        // For example, it hides `T` in `def outer() -> Alias[T]` while `def inner(x: Alias[T])` is evaluated.
        return context.withEmptyTypeAliasStack(() -> {
            Iterable<PyTypeParameterType> typeParameters;
            if (owner instanceof PyClass pyClass) {
                typeParameters = collectTypeParameters(pyClass, context);
            }
            else if (owner instanceof PyFunction pyFunction) {
                typeParameters = collectTypeParameters(pyFunction, context.getTypeContext());
            }
            else {
                typeParameters = Collections.emptyList();
            }
            for (PyTypeParameterType it : typeParameters) {
                if (name.equals(it.getName())) {
                    return it;
                }
            }
            return null;
        });
    }

    // @ApiStatus.Internal
    public static Iterable<PyTypeParameterType> collectTypeParameters(
        PyFunction function,
        TypeEvalContext context
    ) {
        return StreamEx.of(function.getParameterList().getParameters())
            .select(PyNamedParameter.class)
            .map(it -> new PyTypingTypeProvider().getParameterType(
                it,
                function,
                context
            ))
            .append(new PyTypingTypeProvider().getReturnType(function, context))
            .map(PyTypingTypeProvider::deref)
            .map(it -> PyTypeChecker.collectGenerics(it, context))
            .flatMap(it -> {
                return StreamEx.<PyTypeParameterType>of(it.getTypeVars())
                    .append(it.getParamSpecs())
                    .append(it.getTypeVarTuples());
            });
    }

    private static PsiElement getStubRetainedTypeHintContext(PsiElement typeHintExpression) {
        PsiFile containingFile = typeHintExpression.getContainingFile();
        // Values from PSI stubs and regular type comments
        PsiElement fragmentOwner = containingFile.getContext();
        if (fragmentOwner != null) {
            return fragmentOwner;
        }
        else if (containingFile instanceof PyFunctionTypeAnnotationFile || containingFile instanceof PyTypeHintFile) {
            return PyPsiUtils.getRealContext(typeHintExpression);
        }
        else {
            return typeHintExpression;
        }
    }

    public static @Nullable PyPositionalVariadicType getUnpackedType(PsiElement element, TypeEvalContext context) {
        Ref<PyType> typeRef = getTypeFromStarExpression(element, context);
        if (typeRef == null) {
            typeRef = getTypeFromUnpackOperator(element, context);
        }
        if (typeRef == null) {
            return null;
        }
        PyType expressionType = typeRef.get();
        if (expressionType instanceof PyTupleType tupleType) {
            return new PyUnpackedTupleTypeImpl(tupleType.getElementTypes(), tupleType.isHomogeneous());
        }
        if (expressionType instanceof PyTypeVarTupleType typeVarTupleType) {
            return typeVarTupleType;
        }
        return null;
    }

    private static @Nullable Ref<PyType> getTypeFromUnpackOperator(PsiElement element, TypeEvalContext context) {
        if (!(element instanceof PySubscriptionExpression subscriptionExpression) ||
            !resolvesToQualifiedNames(subscriptionExpression.getOperand(), context, UNPACK, UNPACK_EXT)
        ) {
            return null;
        }
        PyExpression indexExpression = subscriptionExpression.getIndexExpression();
        if (!(indexExpression instanceof PyReferenceExpression || indexExpression instanceof PySubscriptionExpression)) {
            return null;
        }
        return Ref.create(PyTypeUtil.derefOrUnknown(getType(indexExpression, context)));
    }

    private static @Nullable Ref<PyType> getTypeFromStarExpression(PsiElement element, TypeEvalContext context) {
        if (!(element instanceof PyStarExpression starExpression)) {
            return null;
        }
        PyExpression starredExpression = starExpression.getExpression();
        if (!(starredExpression instanceof PyReferenceExpression || starredExpression instanceof PySubscriptionExpression)) {
            return null;
        }
        return Ref.create(PyTypeUtil.derefOrUnknown(getType(starredExpression, context)));
    }

    private static List<@Nullable PyType> getIndexTypes(PySubscriptionExpression expression, Context context) {
        PyExpression indexExpr = PyPsiUtils.flattenParens(expression.getIndexExpression());
        List<PyExpression> elements;
        if (indexExpr instanceof PyTupleExpression tupleExpression) {
            elements = List.of(tupleExpression.getElements());
        }
        else {
            elements = indexExpr != null ? List.of(indexExpr) : List.of();
        }
        List<@Nullable PyType> result = new ArrayList<>();
        for (PyExpression it : elements) {
            result.add(PyTypeUtil.derefOrUnknown(getType(it, context)));
        }
        return result;
    }

    /**
     * Parameterizes {@code pyClass} through its generic definition when the raw {@code actualTypeParams} are not in the final shape.
     * This is the case when a type parameter has a default,
     * or when a class generic only over one ParamSpec gets `A[int, str]` as a shorthand for `A[[int, str]]`.
     */
    private static @Nullable PyClassType parameterizeClassThroughDefinition(
        PyClass pyClass,
        List<@Nullable PyType> actualTypeParams,
        Context context
    ) {
        PyClassType genericDefinitionType = RecursionManager.doPreventingRecursion(pyClass, false, () -> {
            return PyTypeChecker.findGenericDefinitionType(pyClass, context.getTypeContext());
        });
        if (genericDefinitionType == null) {
            return null;
        }
        List<PyType> typeParameters = genericDefinitionType.getTypeArguments();
        boolean hasDefaults = typeParameters.stream().anyMatch(it -> it instanceof PyTypeParameterType typeParameterType && typeParameterType.getDefaultType() != null);
        boolean isSingleParamSpecParameterization =
            !actualTypeParams.isEmpty() && typeParameters.size() == 1 && typeParameters.get(0) instanceof PyParamSpecType;
        if (hasDefaults || isSingleParamSpecParameterization) {
            PyType parameterizedType = PyTypeChecker.parameterizeType(genericDefinitionType, actualTypeParams, context.getTypeContext());
            if (parameterizedType instanceof PyClassType parameterizedClassType && parameterizedClassType.isParameterized()) {
                return parameterizedClassType;
            }
        }
        return null;
    }

    // @ApiStatus.Internal
    public static @Nullable PyClassType parameterizeType(PyType type, TypeEvalContext context) {
        if (!(type instanceof PyClassType classType)) {
            return null;
        }
        if (classType instanceof PyTypedDictType) {
            return null;
        }

        if (PyNames.FQN.TUPLE.equals(classType.getClassQName()) && !(classType instanceof PyTupleType)) {
            return PyTupleType.createHomogeneous(classType.getPyClass(), PyAnyType.getUnknown());
        }

        PyTypeChecker.Generics collected = PyTypeChecker.collectGenerics(classType, context);

        // If the type already contains type parameters, keep it as-is
        if (!collected.isEmpty()) {
            return PyTypeChecker.parameterizeType(classType, Collections.emptyList(), context) instanceof PyClassType parameterized
                ? parameterized
                : null;
        }

        PyClassType genericDef = RecursionManager.doPreventingRecursion(classType.getPyClass(), false, () -> {
            return PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), context);
        });
        if (genericDef == null) {
            return null;
        }

        return PyTypeChecker.parameterizeType(genericDef, Collections.emptyList(), context) instanceof PyClassType parameterized
            ? parameterized
            : null;
    }

    private static @Nullable Ref<PyType> getTypeFromTypeAlias(
        PyQualifiedNameOwner alias,
        PsiElement typeHint,
        PsiElement element,
        Context context
    ) {
        if (element instanceof PyExpression expression) {
            if (alias instanceof PyTypeAliasStatement typeAliasStatement) {
                return getTypeFromTypeAliasStatement(typeAliasStatement, typeHint, expression, context);
            }

            Ref<PyType> assignedTypeRef = getType(expression, context, false);
            if (assignedTypeRef != null) {
                PyType assignedType = assignedTypeRef.get();
                if (PyTypeUtilKt.isAnyOrUnknown(assignedType)) {
                    return assignedTypeRef;
                }
                if (typeHint instanceof PySubscriptionExpression subscriptionExpression) {
                    List<@Nullable PyType> indexTypes = getIndexTypes(subscriptionExpression, context);
                    PyType parameterized = PyTypeChecker.parameterizeType(Objects.requireNonNull(assignedType), indexTypes, context.getTypeContext());
                    return Ref.create(parameterized != null ? parameterized : PyAnyType.getUnknown());
                }
                if (typeHint instanceof PyReferenceExpression) {
                    if (!(assignedType instanceof PyTypeParameterType)) {
                        List<PyTypeParameterType> typeAliasTypeParams =
                            PyTypeChecker.collectGenerics(assignedType, context.getTypeContext()).getAllTypeParameters();
                        if (!typeAliasTypeParams.isEmpty()) {
                            return Ref.create(
                                PyTypeChecker.parameterizeType(
                                    Objects.requireNonNull(assignedType),
                                    Collections.emptyList(),
                                    context.getTypeContext()
                                )
                            );
                        }
                        return Ref.create(assignedType);
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable Ref<PyType> getTypeFromTypeAliasStatement(
        PyTypeAliasStatement typeAliasStatement,
        PsiElement typeHint,
        PyExpression assignedExpression,
        Context context
    ) {
        Ref<PyType> assignedTypeRef = getType(assignedExpression, context);
        if (assignedTypeRef != null) {
            PyType assignedType = assignedTypeRef.get();
            if (assignedType == null) {
                return assignedTypeRef;
            }
            List<@Nullable PyType> indexTypes = typeHint instanceof PySubscriptionExpression subscriptionExpression
                ? getIndexTypes(subscriptionExpression, context)
                : Collections.emptyList();

            PyTypeParameterList typeParameterList = typeAliasStatement.getTypeParameterList();
            List<PyTypeParameter> typeParameters = typeParameterList != null ? typeParameterList.getTypeParameters() : Collections.emptyList();
            List<PyType> typeAliasTypeParams = new ArrayList<>();
            for (PyTypeParameter it : typeParameters) {
                PyTypeParameterType typeParameterType = getTypeParameterTypeFromTypeParameter(it, context);
                if (typeParameterType != null) {
                    typeAliasTypeParams.add(typeParameterType);
                }
            }
            if (!typeAliasTypeParams.isEmpty()) {
                PyTypeChecker.GenericSubstitutions substitutions = PyTypeChecker.mapTypeParametersToSubstitutions(
                    typeAliasTypeParams,
                    indexTypes,
                    PyTypeParameterMapping.Option.USE_DEFAULTS,
                    PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY
                );

                return substitutions != null
                    ? Ref.create(PyTypeChecker.substitute(assignedType, substitutions, context.getTypeContext()))
                    : null;
            }
            return assignedTypeRef;
        }
        return null;
    }

    private static @Nullable PyType getParameterizedType(PsiElement element, Context context) {
        if (element instanceof PySubscriptionExpression subscriptionExpression) {
            PyExpression operand = subscriptionExpression.getOperand();
            PyExpression indexExpr = subscriptionExpression.getIndexExpression();
            if (indexExpr != null) {
                PyType operandType = deref(getType(operand, context));
                List<@Nullable PyType> indexTypes = getIndexTypes(subscriptionExpression, context);
                if (operandType != null) {
                    if (operandType instanceof PyNamedTupleType namedTupleType) {
                        PyType parameterized = PyNamedTupleTypeProviderKt.parameterizeNamedTupleType(namedTupleType, indexTypes, context.getTypeContext());
                        if (parameterized != null) {
                            return parameterized;
                        }
                    }
                    if (operandType instanceof PyClassType operandClassType) {
                        if (!(operandClassType instanceof PyTupleType) && PyNames.FQN.TUPLE.equals(operandClassType.getPyClass().getQualifiedName())) {
                            if (indexExpr instanceof PyTupleExpression tupleExpression) {
                                List<@Nullable PyExpression> arguments = new ArrayList<>();
                                for (PyExpression it : tupleExpression.getElements()) {
                                    arguments.add(PyPsiUtils.flattenParens(it));
                                }

                                if (arguments.size() == 2 && arguments.get(arguments.size() - 1) instanceof PyEllipsisLiteralExpression) {  // Homogeneous
                                    if (arguments.get(0) instanceof PyEllipsisLiteralExpression) {
                                        return PyTupleType.createHomogeneous(subscriptionExpression, PyAnyType.getUnknown());
                                    }

                                    PyType indexType = indexTypes.get(0);
                                    if (indexType instanceof PyPositionalVariadicType) {
                                        return PyTupleType.createHomogeneous(subscriptionExpression, PyAnyType.getUnknown());
                                    }
                                    return PyTupleType.createHomogeneous(subscriptionExpression, indexType);
                                }

                                List<@Nullable PyType> elementTypes = new ArrayList<>();
                                for (int i = 0; i < Math.min(arguments.size(), indexTypes.size()); i++) {
                                    PyExpression argument = arguments.get(i);
                                    PyType indexType = indexTypes.get(i);
                                    if (argument instanceof PyEllipsisLiteralExpression) {
                                        elementTypes.add(PyAnyType.getUnknown());
                                    }
                                    else if (argument instanceof PyTupleExpression argumentTuple && argumentTuple.getElements().length == 0) {
                                        elementTypes.add(PyAnyType.getUnknown());
                                    }
                                    else {
                                        elementTypes.add(indexType);
                                    }
                                }
                                return PyTupleType.create(subscriptionExpression, elementTypes);
                            }

                            if (indexExpr instanceof PyEllipsisLiteralExpression) {
                                return PyTupleType.create(subscriptionExpression, Collections.singletonList(PyAnyType.getUnknown()));
                            }
                            return PyTupleType.create(subscriptionExpression, indexTypes);
                        }

                        if (isGeneric(operandClassType, context.getTypeContext())) {
                            PyClassType parameterized = parameterizeClassThroughDefinition(operandClassType.getPyClass(), indexTypes, context);
                            if (parameterized != null) {
                                return parameterized.toInstance();
                            }
                        }
                        else {
                            return null;
                        }
                    }
                    return PyTypeChecker.parameterizeType(operandType, indexTypes, context.getTypeContext());
                }
            }
        }
        return null;
    }

    private static @Nullable PyType getCollection(PsiElement element, TypeEvalContext context) {
        String typingName = getQualifiedName(element);

        String builtinName = typingName != null ? BUILTIN_COLLECTION_CLASSES.get(typingName) : null;
        if (builtinName != null) {
            return PyLegacyDocstringTypeParser.getTypeByName(element, builtinName, context);
        }

        String collectionName = typingName != null ? COLLECTIONS_CLASSES.get(typingName) : null;
        if (collectionName != null) {
            return PyLegacyDocstringTypeParser.getTypeByName(element, collectionName, context);
        }

        return null;
    }

    private static List<PsiElement> tryResolving(PyExpression expression, TypeEvalContext context) {
        List<PsiElement> result = new ArrayList<>();
        for (Pair<@Nullable PyQualifiedNameOwner, PsiElement> it : tryResolvingWithAliases(expression, context, false)) {
            result.add(it.getSecond());
        }
        return result;
    }

    private static List<Pair<@Nullable PyQualifiedNameOwner, PsiElement>> tryResolvingWithAliases(
        PyExpression expression,
        TypeEvalContext context,
        boolean typeRepresentation
    ) {
        List<Pair<@Nullable PyQualifiedNameOwner, PsiElement>> elements = new ArrayList<>();
        if (expression instanceof PyReferenceExpression referenceExpression) {
            List<? extends @Nullable PsiElement> results;
            if (typeRepresentation) {
                results = resolveTypeRepresentationReference(referenceExpression, context);
            }
            else {
                if (context.maySwitchToAST(referenceExpression)) {
                    PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
                    results = PyUtil.multiResolveTopPriority(referenceExpression, resolveContext);
                }
                else {
                    results = tryResolvingOnStubs(referenceExpression, context);
                }
            }
            for (PsiElement element : results) {
                PyClass cls = PyUtil.turnConstructorIntoClass(element instanceof PyFunction function ? function : null);
                if (cls != null) {
                    elements.add(Pair.create(null, cls));
                    continue;
                }
                String name = element != null ? getQualifiedName(element) : null;
                if (name != null && OPAQUE_NAMES.contains(name)) {
                    elements.add(Pair.create(null, element));
                    continue;
                }
                // Presumably, a TypeVar definition or a type alias
                if (element instanceof PyTargetExpression targetExpression) {
                    PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(targetExpression);
                    if (assignedValue != null) {
                        PyExpression typeAliasTypeValue =
                            assignedValue instanceof PyCallExpression assignedCall ? getTypeAliasTypeValue(assignedCall, context) : null;
                        elements.add(Pair.create(targetExpression, typeAliasTypeValue != null ? typeAliasTypeValue : assignedValue));
                        continue;
                    }
                }
                if (element instanceof PyTypeAliasStatement typeAliasStatement) {
                    PyExpression assignedValue;
                    if (context.maySwitchToAST(typeAliasStatement)) {
                        assignedValue = typeAliasStatement.getTypeExpression();
                    }
                    else {
                        String assignedTypeText = typeAliasStatement.getTypeExpressionText();
                        assignedValue =
                            assignedTypeText != null ? PyUtil.createExpressionFromFragment(assignedTypeText, typeAliasStatement) : null;
                    }
                    if (assignedValue != null) {
                        elements.add(Pair.create(typeAliasStatement, assignedValue));
                        continue;
                    }
                }
                if (element != null) {
                    elements.add(Pair.create(null, element));
                }
            }
        }
        if (expression instanceof PySubscriptionExpression subscriptionExpression) {
            // Possibly a parameterized type alias
            PyExpression operandExpression = subscriptionExpression.getOperand();
            List<Pair<@Nullable PyQualifiedNameOwner, PsiElement>> results =
                tryResolvingWithAliases(operandExpression, context, typeRepresentation);
            for (Pair<@Nullable PyQualifiedNameOwner, PsiElement> pair : results) {
                // If the parameterized type is a type alias
                if (pair.getFirst() != null) {
                    elements.add(pair);
                }
            }
        }
        return elements.isEmpty() ? List.of(Pair.create(null, expression)) : elements;
    }

    private static List<@Nullable PsiElement> resolveTypeRepresentationReference(PyReferenceExpression expression, TypeEvalContext context) {
        QualifiedName qualifiedName = expression.asQualifiedName();
        if (qualifiedName == null) {
            return Collections.emptyList();
        }
        PsiFile contextFile = FileContextUtil.getContextFile(expression);
        if (contextFile == null) {
            return Collections.emptyList();
        }
        List<@Nullable PsiElement> resolved = PyPsiFacadeImpl.resolveQName(qualifiedName, contextFile);
        if (!resolved.isEmpty()) {
            return resolved;
        }
        PsiElement byTypeEngineModuleName = PyTypeRepresentationResolveKt.resolveByTypeEngineModuleName(qualifiedName, contextFile, context);
        return byTypeEngineModuleName != null ? List.of(byTypeEngineModuleName) : Collections.emptyList();
    }

    private static List<? extends @Nullable PsiElement> tryResolvingOnStubs(
        PyReferenceExpression expression,
        TypeEvalContext context
    ) {
        QualifiedName qualifiedName = expression.asQualifiedName();
        PyFile pyFile = FileContextUtil.getContextFile(expression) instanceof PyFile file ? file : null;

        PsiFile containingFile = expression.getContainingFile();
        PsiElement anchor = containingFile.getContext();
        ScopeOwner scopeOwner;
        if (anchor == null) {
            scopeOwner = pyFile;
        }
        // The scope owner of a fragment knows that a parameter or return annotation is resolved outside of the function
        else if (containingFile instanceof PyExpressionCodeFragment) {
            scopeOwner = ScopeUtil.getScopeOwner(containingFile);
        }
        else if (anchor instanceof ScopeOwner anchorScopeOwner) {
            scopeOwner = anchorScopeOwner;
        }
        else {
            scopeOwner = ScopeUtil.getScopeOwner(anchor);
        }

        if (scopeOwner != null && qualifiedName != null) {
            String unqualifiedName = qualifiedName.getFirstComponent();
            boolean shouldPreferFileScopeForBuiltinGenericAlias = qualifiedName.getComponentCount() == 1 &&
                                                                 TYPING_BUILTINS_GENERIC_ALIASES.containsKey(unqualifiedName) &&
                                                                 pyFile != null &&
                                                                 scopeOwner instanceof PyClass;

            if (shouldPreferFileScopeForBuiltinGenericAlias) {
                List<PsiElement> fileLevelResults = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, pyFile, context);
                if (!fileLevelResults.isEmpty()) {
                    return fileLevelResults;
                }
            }

            return PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context);
        }
        List<@Nullable PsiElement> result = new ArrayList<>();
        result.add(expression);
        return result;
    }

    public static Collection<String> resolveToQualifiedNames(PyExpression expression, TypeEvalContext context) {
        Set<String> result = new LinkedHashSet<>();
        for (PsiElement element : tryResolving(expression, context)) {
            String qName = getQualifiedName(element);
            if (qName == null) {
                continue;
            }
            // For builtins, QualifiedNameFinder now yields "builtins.<name>". Expose the short form too so callers
            // matching against bare builtin names (e.g. PyNames.TYPE) keep working.
            String shortName = PyNames.FQN.unqualifyBuiltinName(qName);
            result.add(qName);
            if (shortName != null && !shortName.equals(qName)) {
                result.add(shortName);
            }
        }
        return result;
    }

    private static @Nullable String getQualifiedName(PsiElement element) {
        if (element instanceof PyQualifiedNameOwner qualifiedNameOwner) {
            return qualifiedNameOwner.getQualifiedName();
        }
        else if (element instanceof PyQualifiedExpression qualifiedExpression) {
            return String.valueOf(qualifiedExpression.asQualifiedName());
        }
        else {
            return null;
        }
    }

    public static @Nullable PyType toAsyncIfNeeded(PyFunction function, @Nullable PyType returnType) {
        if (function.isAsync() && function.isAsyncAllowed()) {
            if (!function.isGenerator()) {
                return wrapInCoroutineType(returnType, function);
            }
            GeneratorTypeDescriptor desc = GeneratorTypeDescriptor.fromGenerator(returnType);
            if (desc != null) {
                PyClass classType = PyPsiFacade.getInstance(function.getProject()).createClassByQName(ASYNC_GENERATOR, function);
                List<@Nullable PyType> generics = Arrays.asList(desc.yieldType(), desc.sendType());
                return classType != null ? new PyCollectionTypeImpl(classType, false, generics) : PyAnyType.getUnknown();
            }
        }
        return returnType;
    }

    /**
     * Bound narrowed types shouldn't leak out of its scope, since it is bound to a particular call site.
     */
    public static @Nullable PyType removeNarrowedTypeIfNeeded(@Nullable PyType type) {
        if (type instanceof PyNarrowedType narrowedType && narrowedType.isBound()) {
            PyType boolType = PyBuiltinCache.getInstance(Objects.requireNonNull(narrowedType.getOriginal())).getBoolType();
            return boolType != null ? boolType : PyAnyType.getUnknown();
        }
        else {
            return type;
        }
    }

    private static @Nullable PyType wrapInCoroutineType(@Nullable PyType type, PsiElement anchor) {
        PyPsiFacade facade = PyPsiFacade.getInstance(anchor.getProject());
        PyClass targetClass = facade.createClassByQName(PyNames.FQN.COROUTINE_TYPE, anchor);
        if (targetClass == null) {
            targetClass = facade.createClassByQName(COROUTINE, anchor);
        }
        if (targetClass == null) {
            return PyAnyType.getUnknown();
        }
        return new PyCollectionTypeImpl(
            targetClass,
            false,
            Arrays.asList(PyAnyType.getUnknown(), PyAnyType.getUnknown(), type)
        );
    }

    public static @Nullable PyType wrapInGeneratorType(
        @Nullable PyType elementType,
        @Nullable PyType sendType,
        @Nullable PyType returnType,
        PsiElement anchor
    ) {
        PyClass generator = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(GENERATOR, anchor);
        return generator != null ? new PyCollectionTypeImpl(
            generator,
            false,
            Arrays.asList(elementType, sendType, returnType)
        )
            : PyAnyType.getUnknown();
    }

    public static @Nullable Ref<PyType> unwrapCoroutineReturnType(@Nullable PyType coroutineType) {
        if (!(coroutineType instanceof PyClassType classType) || !classType.isParameterized()) {
            return null;
        }
        String qName = classType.getClassQName();

        if (AWAITABLE.equals(qName)) {
            return Ref.create(getOrNull(classType.getTypeArguments(), 0));
        }

        if (COROUTINE.equals(qName) || PyNames.FQN.COROUTINE_TYPE.equals(qName)) {
            return Ref.create(getOrNull(classType.getTypeArguments(), 2));
        }

        return null;
    }

    public static @Nullable Ref<PyType> coroutineOrGeneratorElementType(@Nullable PyType coroutineOrGeneratorType) {
        if (!(coroutineOrGeneratorType instanceof PyClassType classType) || !classType.isParameterized()) {
            return null;
        }
        String qName = classType.getClassQName();

        if (AWAITABLE.equals(qName)) {
            return Ref.create(getOrNull(classType.getTypeArguments(), 0));
        }

        if (COROUTINE.equals(qName) || PyNames.FQN.COROUTINE_TYPE.equals(qName) || GENERATOR.equals(qName)) {
            return Ref.create(getOrNull(classType.getTypeArguments(), 2));
        }

        return null;
    }

    private static <T> @Nullable T getOrNull(List<T> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /**
     * Returns the value of a `TypeAliasType(name, value, ...)` call, or {@code null} if {@code call} does not create a `TypeAliasType`.
     */
    // @ApiStatus.Internal
    public static @Nullable PyExpression getTypeAliasTypeValue(PyCallExpression call, TypeEvalContext context) {
        if (!call.isCalleeText("TypeAliasType")) {
            return null;
        }
        if (!(call.getCallee() instanceof PyReferenceExpression callee)) {
            return null;
        }
        if (!resolvesToQualifiedNames(callee, context, TYPE_ALIAS_TYPE, TYPE_ALIAS_TYPE_EXT)) {
            return null;
        }
        return call.getArgument(1, "value", PyExpression.class);
    }

    /**
     * Checks whether the given assignment is type hinted with `typing.TypeAlias`.
     * <p>
     * <p>
     * It can be done either with a variable annotation or a type comment.
     */
    public static boolean isExplicitTypeAlias(PyAssignmentStatement assignment, TypeEvalContext context) {
        PyExpression[] targets = assignment.getTargets();
        if (!(targets.length > 0 && targets[0] instanceof PyTargetExpression target)) {
            return false;
        }
        return isExplicitTypeAlias(target, context);
    }

    public static boolean isExplicitTypeAlias(PyTargetExpression targetExpression, TypeEvalContext context) {
        PyExpression annotationValue = getAnnotationValue(targetExpression, context);
        if (annotationValue instanceof PyReferenceExpression) {
            return resolvesToQualifiedNames(annotationValue, context, TYPE_ALIAS, TYPE_ALIAS_EXT);
        }
        String typeCommentAnnotation = targetExpression.getTypeCommentAnnotation();
        if (typeCommentAnnotation != null) {
            PyExpression commentValue = PyUtil.createExpressionFromFragment(typeCommentAnnotation, targetExpression);
            if (commentValue instanceof PyReferenceExpression) {
                return resolvesToQualifiedNames(commentValue, context, TYPE_ALIAS, TYPE_ALIAS_EXT);
            }
        }
        return false;
    }

    /**
     * Detects whether the given element belongs to a self-evident type hint. Namely, these are:
     * <ul>
     *  <li>function and variable annotations</li>
     *  <li>type comments</li>
     *  <li>explicit type aliases marked with `TypeAlias`</li>
     * </ul>
     * Note that `element` can belong to their AST directly or be a part of an injection inside one of such elements.
     */
    public static boolean isInsideTypeHint(PsiElement element, TypeEvalContext context) {
        PsiElement realContext = PyPsiUtils.getRealContext(element);

        if (PsiTreeUtil.getParentOfType(realContext, PyAnnotation.class, false, PyStatement.class) != null) {
            return true;
        }

        PsiComment comment = PsiTreeUtil.getParentOfType(realContext, PsiComment.class, false, PyStatement.class);
        if (comment != null && getTypeCommentValue(comment.getText()) != null) {
            return true;
        }

        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(
            realContext,
            PyAssignmentStatement.class,
            false,
            PyStatement.class
        );
        if (assignment != null &&
            PsiTreeUtil.isAncestor(assignment.getAssignedValue(), realContext, false) &&
            isExplicitTypeAlias(assignment, context)
        ) {
            return true;
        }

        PyTypeAliasStatement typeAlias = PsiTreeUtil.getParentOfType(
            realContext,
            PyTypeAliasStatement.class,
            false,
            PyStatement.class
        );
        return typeAlias != null && PsiTreeUtil.isAncestor(typeAlias.getTypeExpression(), realContext, false);
    }

    private static <T> T staticWithCustomContext(TypeEvalContext context, Function<Context, T> delegate) {
        return staticWithCustomContext(context, false, delegate);
    }

    private static <T> T staticWithCustomContext(
        TypeEvalContext context,
        boolean typeRepresentationMode,
        Function<Context, T> delegate
    ) {
        Context customContext = context.getProcessingContext().get(TYPE_HINT_EVAL_CONTEXT);
        boolean firstEntrance = customContext == null;
        if (firstEntrance) {
            customContext = new Context(context, typeRepresentationMode);
            context.getProcessingContext().put(TYPE_HINT_EVAL_CONTEXT, customContext);
        }
        try {
            return delegate.apply(customContext);
        }
        finally {
            if (firstEntrance) {
                context.getProcessingContext().put(TYPE_HINT_EVAL_CONTEXT, null);
            }
        }
    }

    /**
     * Tells if the solver may set this type variable to `None`.
     * This is the case when the type variable has no bound, a bound that accepts `None`, or a constraint that accepts `None`.
     */
    private static boolean admitsNone(PyTypeVarType typeVar, @Nullable PyType noneType, TypeEvalContext context) {
        List<@Nullable PyType> constraints = typeVar.getConstraints();
        if (!constraints.isEmpty()) {
            return noneType != null && constraints.stream().anyMatch(it -> PyTypeChecker.match(it, noneType, context));
        }
        PyType bound = typeVar.getBound();
        return bound == null || noneType != null && PyTypeChecker.match(bound, noneType, context);
    }
}
