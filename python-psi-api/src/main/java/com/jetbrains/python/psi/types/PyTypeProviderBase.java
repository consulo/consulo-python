// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PyTypeProviderBase implements PyTypeProvider {

    @Override
    public @Nullable PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param,
                                                  PyFunction func,
                                                  TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getCallType(PyFunction function,
                                             PyElement callSite,
                                             TypeEvalContext context) {
        if (callSite instanceof PyCallSiteExpression callSiteExpression) {
            return getCallType(function, callSiteExpression, context);
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getCallType(PyFunction function,
                                             PyCallSiteExpression callSite,
                                             TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable PyType getCallableType(PyCallable callable, TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable PyType getGenericType(PyClass cls, TypeEvalContext context) {
        return null;
    }

    @Override
    public Map<PyType, PyType> getGenericSubstitutions(PyClass cls, TypeEvalContext context) {
        return Collections.emptyMap();
    }

    @Override
    public @Nullable Ref<@Nullable PyType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                    PyExpression callee,
                                                                    TypeEvalContext context) {
        return null;
    }

    @Override
    public @Nullable List<PyTypeMember> getMemberTypes(PyType type,
                                                                String name,
                                                                @Nullable PyExpression location,
                                                                AccessDirection direction,
                                                                PyResolveContext context) {
        return null;
    }
}
