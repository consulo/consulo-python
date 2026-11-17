// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.DataclassParameterArgumentMapping;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.impl.psi.types.PyOverloadType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Top-level declarations of PyDataclassTransformResolver.kt
 */
public final class PyDataclassTransformResolverKt {
    static final String CONVERTER = "converter";

    private PyDataclassTransformResolverKt() {
    }

    /**
     * The type that the converter {@code converterExpr} accepts. It is the union of the types of the first positional parameter of each signature.
     * A class converter uses the signatures of its constructor.
     */
    static @Nullable PyType getInputTypeOfConverter(PyExpression converterExpr, TypeEvalContext context) {
        PyType converterType = context.getType(converterExpr);
        if (converterType == null) {
            return null;
        }

        List<PyCallableType> callableTypes = new ArrayList<>();
        for (PyType type : PyTypeUtil.asUnionSequence(converterType)) {
            if (type instanceof PyClassType classType && classType.isDefinition()) {
                PyType constructorType =
                    PyCallExpressionHelper.createCallableFromClass(classType, PyResolveContext.defaultContext(context));
                if (constructorType instanceof PyCallableType callableType) {
                    callableTypes.add(callableType);
                }
                else if (constructorType instanceof PyOverloadType overloadType) {
                    for (PyCallableType item : overloadType.getItems()) {
                        if (item != null) {
                            callableTypes.add(item);
                        }
                    }
                }
            }
            else if (type instanceof PyCallableType callableType) {
                callableTypes.add(callableType);
            }
            else if (type instanceof PyOverloadType overloadType) {
                for (PyCallableType item : overloadType.getItems()) {
                    if (item != null) {
                        callableTypes.add(item);
                    }
                }
            }
        }

        List<@Nullable PyType> firstArgumentTypes = new ArrayList<>();
        for (PyCallableType callableType : callableTypes) {
            List<PyCallableParameter> parameters = callableType.getParameters(context);
            if (parameters == null) {
                continue;
            }
            for (PyCallableParameter param : parameters) {
                if (!param.isPositionOnlySeparator() &&
                    !param.isKeywordOnlySeparator() &&
                    !param.isKeywordContainer() &&
                    !(param.getParameter() instanceof PyNamedParameter namedParameter && namedParameter.isKeywordOnly())) {
                    firstArgumentTypes.add(param.getArgumentType(context));
                    break;
                }
            }
        }

        return !firstArgumentTypes.isEmpty() ? PyUnionType.unionOrUnknown(firstArgumentTypes) : null;
    }

    /**
     * The type that the converter {@code converterExpr} returns: the union of the return types of its signatures.
     */
    static @Nullable PyType getReturnTypeOfConverter(PyExpression converterExpr, TypeEvalContext context) {
        PyType converterType = context.getType(converterExpr);
        if (converterType == null) {
            return null;
        }

        List<@Nullable PyType> returnTypes = new ArrayList<>();
        for (PyType type : PyTypeUtil.asUnionSequence(converterType)) {
            if (type instanceof PyCallableType callableType) {
                PyType returnType = callableType.getReturnType(context);
                if (returnType != null) {
                    returnTypes.add(returnType);
                }
            }
            else if (type instanceof PyOverloadType overloadType) {
                for (PyCallableType it : overloadType.getItems()) {
                    PyType returnType = it.getReturnType(context);
                    if (returnType != null) {
                        returnTypes.add(returnType);
                    }
                }
            }
        }

        return !returnTypes.isEmpty() ? PyUnionType.unionOrUnknown(returnTypes) : null;
    }

    static @Nullable Boolean getArgumentDefault(String paramName, PyFunction function) {
        PyNamedParameter parameter = function.getParameterList().findParameterByName(paramName);
        String defaultValueText = parameter != null ? parameter.getDefaultValueText() : null;
        if (PyNames.TRUE.equals(defaultValueText)) {
            return true;
        }
        else if (PyNames.FALSE.equals(defaultValueText)) {
            return false;
        }
        else {
            return null;
        }
    }

    /**
     * Detects a PEP 681 `dataclass_transform`-based class from core `dataclass_transform`-compatible keyword arguments on a
     * decorator or in the superclass list. This is the framework-agnostic transform detection only; Pydantic-specific
     * detection lives in the `intellij.python.pydantic` module's provider. Consumed by `parseDataclassParametersFromAST`.
     */
    public static @Nullable Pair<PyDataclassStub, DataclassParameterArgumentMapping> buildDataclassTransformStubAndMapping(
        PyClass cls,
        @Nullable TypeEvalContext context
    ) {
        // Process decorators that have dataclass_transform-compatible keyword arguments.
        PyDecoratorList decoratorList = cls.getDecoratorList();
        if (decoratorList != null) {
            for (PyDecorator decorator : decoratorList.getDecorators()) {
                QualifiedName qualifiedName = decorator.getQualifiedName();
                if (qualifiedName != null) {
                    List<PyKeywordArgument> decoratorKeywordArguments = keywordArguments(decorator.getArguments());
                    if (decoratorKeywordArguments.stream()
                        .anyMatch(it -> PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS.contains(it.getName()))) {
                        PyDataclassParametersBuilder builder = new PyDataclassParametersBuilder(PyDataclassTransformType.INSTANCE, qualifiedName);
                        for (PyKeywordArgument it : decoratorKeywordArguments) {
                            if (PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS.contains(it.getName())) {
                                builder.update(it.getKeyword(), it);
                            }
                        }
                        return builder.build();
                    }
                }
            }
        }

        // Process dataclass_transform-compatible keyword argument in the list of superclasses.
        PyArgumentList superclassList = cls.getSuperClassExpressionList();
        if (superclassList != null) {
            List<PyKeywordArgument> classKeywordArguments = keywordArguments(superclassList.getArguments());
            if (classKeywordArguments.stream()
                .anyMatch(it -> PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS.contains(it.getName()))) {
                PyDataclassParametersBuilder builder = new PyDataclassParametersBuilder(PyDataclassTransformType.INSTANCE, null);
                for (PyKeywordArgument it : classKeywordArguments) {
                    if (PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS.contains(it.getName())) {
                        builder.update(it.getKeyword(), it);
                    }
                }
                return builder.build();
            }
        }

        return null;
    }

    private static List<PyKeywordArgument> keywordArguments(PyExpression[] arguments) {
        List<PyKeywordArgument> result = new ArrayList<>();
        for (PyExpression argument : arguments) {
            if (argument instanceof PyKeywordArgument keywordArgument) {
                result.add(keywordArgument);
            }
        }
        return result;
    }

    public static boolean isDataclassTransformOnMetaclass(
        PyDecorator decorator,
        PyClass pyClass,
        TypeEvalContext context
    ) {
        if (!(pyClass.getMetaClassType(true, context) instanceof PyClassType metaClassType)) {
            return false;
        }
        PyClass metaclass = metaClassType.getPyClass();
        PyDecoratorList decoratorList = metaclass.getDecoratorList();
        return decoratorList != null && Arrays.stream(decoratorList.getDecorators()).anyMatch(it -> it.equals(decorator));
    }

    public static boolean isDataclassTransform(PyDecorator decorator, TypeEvalContext context) {
        return findDataclassTransformDecorator(resolveDecoratorStubSafe(decorator, context).stream(), context) != null;
    }

    static @Nullable PyDecorator findDataclassTransformDecorator(Stream<? extends PsiElement> targets, TypeEvalContext context) {
        return targets
            .<PsiElement>flatMap(it -> {
                // ResolveResult prioritisation in PyResolveUtil.resolveQualifiedNameInScope
                // returns only the implementation if it's present.
                if (it instanceof PyFunction function && !PyiUtil.isOverload(function, context)) {
                    return Stream.concat(PyiUtil.getOverloads(function, context).stream(), Stream.of(function));
                }
                else {
                    return Stream.of(it);
                }
            })
            .filter(it -> it instanceof PyDecoratable)
            .map(it -> (PyDecoratable) it)
            .flatMap(it -> {
                PyDecoratorList decoratorList = it.getDecoratorList();
                return decoratorList != null ? Arrays.stream(decoratorList.getDecorators()) : Stream.empty();
            })
            .filter(it -> {
                QualifiedName qualifiedName = it.getQualifiedName();
                return qualifiedName != null &&
                    PyDataclassNames.DataclassTransform.DATACLASS_TRANSFORM_NAME.equals(qualifiedName.getLastComponent());
            })
            .findFirst()
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static List<PsiElement> resolveDecoratorStubSafe(PyDecorator decorator, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<PsiElement> result = StubAwareComputation.on(decorator)
            .overAst(psi -> (List<PsiElement>) (List<?>) psi.multiResolveCalleeFunction(resolveContext))
            .overStub(stub -> {
                QualifiedName qualifiedName = stub != null ? stub.getQualifiedName() : null;
                return qualifiedName != null
                    ? PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, (ScopeOwner) decorator.getContainingFile(), context)
                    : null;
            })
            .overAstStubLike(psi -> {
                QualifiedName qualifiedName = psi.getQualifiedName();
                return qualifiedName != null
                    ? PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, (ScopeOwner) decorator.getContainingFile(), context)
                    : null;
            })
            .compute(context);
        return result != null ? result : Collections.emptyList();
    }
}
