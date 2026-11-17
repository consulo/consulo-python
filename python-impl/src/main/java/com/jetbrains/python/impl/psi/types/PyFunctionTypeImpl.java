// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyTypeParameterListOwner;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ArgumentMappingResults;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.util.collection.Lists;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Type of particular function that is represented as a {@link PyCallable} in the PSI tree.
 */
public class PyFunctionTypeImpl implements PyFunctionType {

    private final PyCallable myCallable;
    private final List<PyCallableParameter> myParameters;

    public PyFunctionTypeImpl(PyCallable callable, List<PyCallableParameter> parameters) {
        myCallable = callable;
        myParameters = parameters;
    }

    @Override
    public @Nullable List<PyTypeParameterType> getTypeParameters(TypeEvalContext context) {
        if (!(myCallable instanceof PyTypeParameterListOwner owner)) {
            return null;
        }
        PyTypeParameterList typeParameterList = owner.getTypeParameterList();
        if (typeParameterList == null) {
            return null;
        }
        List<PyTypeParameterType> result = new ArrayList<>();
        for (PyTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
            var type = PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(typeParameter, context);
            if (type != null) {
                result.add(type);
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context) {
        return context.getReturnType(myCallable);
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context,
                                        @Nullable PyElement callSite,
                                        List<PyCallableArgument> arguments) {
        if (!(myCallable instanceof PyFunction function)) {
            return context.getReturnType(myCallable);
        }

        if (callSite != null) {
            for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
                Ref<PyType> typeRef = typeProvider.getCallType(function, callSite, context);
                if (typeRef != null) {
                    PyType type = typeRef.get();
                    if (type != null) {
                        type.assertValid(typeProvider.toString());
                    }
                    return type;
                }
            }
        }

        PyCallableParameterListTypeImpl parametersType = new PyCallableParameterListTypeImpl(myParameters);
        ArgumentMappingResults mappingResults = PyCallExpressionHelper.analyzeArguments(arguments, parametersType, context);
        Map<PyCallableArgument, PyCallableParameter> parameters = mappingResults.getMappedParameters();
        @Nullable PyType type = context.getReturnType(function);
        if (PyTypeChecker.hasGenerics(type, context)) {
            PyType callableType = context.getType(function);
            PyCallableType callableTypeCasted = callableType instanceof PyCallableType ? (PyCallableType)callableType : null;
            var substitutions =
                PyTypeInferenceCspFactory.unifyGenericCall(callSite, callableTypeCasted, parameters, context);
            if (substitutions != null) {
                var substitutionsWithUnresolvedReturnGenerics =
                    PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(callableTypeCasted, type, substitutions, context);
                type = PyTypeChecker.substitute(type, substitutionsWithUnresolvedReturnGenerics, context);
            }
            else {
                type = PyAnyType.getUnknown();
            }
        }
        if (!isUnknown(type) && isDynamicallyEvaluated(parameters.values(), context)) {
            type = PyUnionType.createWeakType(type);
        }
        return PyNarrowedType.bindIfNeeded(type, callSite);
    }

    private static boolean isDynamicallyEvaluated(Collection<PyCallableParameter> parameters, TypeEvalContext context) {
        for (PyCallableParameter parameter : parameters) {
            PyType type = parameter.getType(context);
            if (type instanceof PyDynamicallyEvaluatedType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<PyCallableParameter> getParameters(TypeEvalContext context) {
        return myParameters;
    }

    @Override
    public @Nullable List<PyCallableParameter> getUnpackedParameters(TypeEvalContext context) {
        PyCallableParameterListType callableParameterListType = as(getParametersType(context), PyCallableParameterListType.class);
        if (callableParameterListType != null) {
            return callableParameterListType.getUnpackedParameters(context);
        }
        return getParameters(context);
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        List<PyCallableParameter> parameters = Lists.notNullize(getParameters(context));
        return new PyCallableParameterListTypeImpl(parameters);
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
    public Object [] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
        PyExpression callee = location instanceof PyReferenceExpression re ? re.getQualifier() : null;
        PyClassType delegate = PyUtil.selectCallableTypeRuntimeClass(this, callee, typeEvalContext);
        return delegate != null ? delegate.getCompletionVariants(completionPrefix, location, context) : ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {
    }

    @Override
    public PyCallable getCallable() {
        return myCallable;
    }

    @Override
    public PyFunctionType dropSelf(TypeEvalContext context) {
        List<PyCallableParameter> parameters = Lists.notNullize(getParameters(context));

        List<PyCallableParameter> newParams = ParamHelper.dropSelf(parameters);
        return newParams.size() < parameters.size() ? new PyFunctionTypeImpl(myCallable, newParams) : this;
    }

    @Override
    public String toString() {
        return "PyFunctionType: " + getName();
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyFunctionType(this);
    }
}
