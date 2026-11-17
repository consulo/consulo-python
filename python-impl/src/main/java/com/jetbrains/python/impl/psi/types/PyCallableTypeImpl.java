// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.util.collection.Lists;

public class PyCallableTypeImpl implements PyCallableType {
    private final @Nullable List<PyTypeParameterType> myTypeParameters;
    private final @Nullable PyCallableParameterVariadicType myParametersType;
    private final @Nullable PyType myReturnType;
    private final @Nullable PyCallable myCallable;
    private final PyFunction.@Nullable Modifier myModifier;

    public PyCallableTypeImpl(@Nullable PyCallableParameterVariadicType parametersType, @Nullable PyType returnType) {
        this(null, parametersType, returnType, null, null);
    }

    public PyCallableTypeImpl(@Nullable List<PyTypeParameterType> typeParameters,
                              @Nullable PyCallableParameterVariadicType parametersType,
                              @Nullable PyType returnType,
                              @Nullable PyCallable callable,
                              PyFunction.@Nullable Modifier modifier) {
        myTypeParameters = typeParameters;
        myParametersType = parametersType;
        PyAnyType.validate(returnType);
        myReturnType = returnType;
        myCallable = callable;
        myModifier = modifier;
    }

    public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters, @Nullable PyType returnType) {
        this(null, parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null, returnType, null, null);
    }

    public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters,
                              @Nullable PyType returnType,
                              @Nullable PyCallable callable,
                              PyFunction.@Nullable Modifier modifier) {
        this(null, parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null, returnType, callable, modifier);
    }

    public static PyCallableTypeImpl withUnknownParameters(@Nullable PyType returnType) {
        return new PyCallableTypeImpl(null, null, returnType, null, null);
    }

    @Override
    public @Nullable List<PyTypeParameterType> getTypeParameters(TypeEvalContext context) {
        return myTypeParameters;
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context) {
        return myReturnType;
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context,
                                        @Nullable PyElement callSite,
                                        List<PyCallableArgument> arguments) {
        if (callSite != null && myCallable instanceof PyFunction function) {
            for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
                Ref<PyType> typeRef = typeProvider.getCallType(function, callSite, context);
                if (typeRef != null) {
                    return typeRef.get();
                }
            }
        }

        if (!PyTypeChecker.hasGenerics(myReturnType, context)) {
            return PyNarrowedType.bindIfNeeded(myReturnType, callSite);
        }

        PyCallableParameterListType parametersType = new PyCallableParameterListTypeImpl(Lists.notNullize(getParameters(context)));
        var mappedParameters = PyCallExpressionHelper.analyzeArgumentsWithDefaults(arguments, parametersType, context);
        var substitutions = PyTypeChecker.unifyGenericCall(mappedParameters, context);
        var substitutionsWithUnresolvedReturnGenerics =
            PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(this, myReturnType, substitutions, context);
        PyType typeAfterSubstitution = PyTypeChecker.substitute(myReturnType, substitutionsWithUnresolvedReturnGenerics, context);
        return PyNarrowedType.bindIfNeeded(typeAfterSubstitution, callSite);
    }

    @Override
    public @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        if (myParametersType instanceof PyCallableParameterListType parameterListType) {
            return parameterListType.getParameters();
        }
        // For backward compatibility with code that expects a single parameter wrapping ParamSpec/Concatenate
        if (myParametersType instanceof PyParamSpecType || myParametersType instanceof PyConcatenateType) {
            return Collections.singletonList(PyCallableParameterImpl.nonPsi(myParametersType));
        }
        return null;
    }

    @Override
    public @Nullable List<PyCallableParameter> getUnpackedParameters(TypeEvalContext context) {
        List<PyCallableParameter> parameters = getParameters(context);
        if (parameters == null) {
            return null;
        }
        return ParamHelper.unpackContainerParameters(parameters, context);
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        return myParametersType;
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        PyClassType delegate = PyUtil.selectCallableTypeRuntimeClass(this, location, resolveContext.getTypeEvalContext());
        return delegate != null ? delegate.resolveMember(name, location, direction, resolveContext) : Collections.emptyList();
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
        PyExpression callee = location instanceof PyReferenceExpression re ? re.getQualifier() : null;
        PyClassType delegate = PyUtil.selectCallableTypeRuntimeClass(this, callee, typeEvalContext);
        return delegate != null ? delegate.getCompletionVariants(completionPrefix, location, context) : ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public String toString() {
        return "PyCallableType: " + getName();
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {
    }

    @Override
    public @Nullable PyCallable getCallable() {
        return myCallable;
    }

    @Override
    public PyFunction.@Nullable Modifier getModifier() {
        return myModifier;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getDeclarationElement() {
        return myCallable;
    }

    @Override
    public PyCallableType dropSelf(TypeEvalContext context) {
        if (!(myParametersType instanceof PyCallableParameterListType)) {
            return this;
        }

        List<PyCallableParameter> parameters = getParameters(context);
        if (ContainerUtil.isEmpty(parameters)) {
            return this;
        }

        if (parameters.getFirst().isSelf()) {
            List<PyCallableParameter> newParameters = ContainerUtil.subList(parameters, 1);
            return new PyCallableTypeImpl(myTypeParameters,
                                          new PyCallableParameterListTypeImpl(newParameters),
                                          myReturnType, myCallable, myModifier);
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyCallableTypeImpl type = (PyCallableTypeImpl)o;
        return Objects.equals(myTypeParameters, type.myTypeParameters) &&
               Objects.equals(myParametersType, type.myParametersType) &&
               Objects.equals(myReturnType, type.myReturnType) &&
               Objects.equals(myCallable, type.myCallable) &&
               myModifier == type.myModifier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(myTypeParameters, myParametersType, myReturnType, myCallable, myModifier);
    }
}
