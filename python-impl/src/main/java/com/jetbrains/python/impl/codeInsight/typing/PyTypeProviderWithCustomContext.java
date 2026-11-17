// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public abstract class PyTypeProviderWithCustomContext<Context> extends PyTypeProviderBase {
    @Override
    public final @Nullable PyType getReferenceExpressionType(PyReferenceExpression referenceExpression,
                                                             TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getReferenceExpressionType(referenceExpression, customContext);
        });
    }

    protected @Nullable PyType getReferenceExpressionType(PyReferenceExpression expression, Context context) {
        return null;
    }

    @Override
    public final @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget,
                                              TypeEvalContext context,
                                              @Nullable PsiElement anchor) {
        return withCustomContext(context, customContext -> {
            return getReferenceType(referenceTarget, customContext, anchor);
        });
    }

    public @Nullable Ref<PyType> getReferenceType(PsiElement target, Context context, @Nullable PsiElement anchor) {
        return null;
    }

    @Override
    public final @Nullable Ref<PyType> getParameterType(PyNamedParameter param,
                                                        PyFunction func,
                                                        TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getParameterType(param, func, customContext);
        });
    }

    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, Context context) {
        return null;
    }

    @Override
    public final @Nullable Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getReturnType(callable, customContext);
        });
    }

    public @Nullable Ref<PyType> getReturnType(PyCallable callable, Context context) {
        return null;
    }

    @Override
    public final @Nullable Ref<PyType> getCallType(PyFunction function,
                                                   PyCallSiteExpression callSite,
                                                   TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getCallType(function, callSite, customContext);
        });
    }

    public @Nullable Ref<PyType> getCallType(PyFunction function, PyElement site, Context context) {
        return null;
    }

    @Override
    public final @Nullable PyType getContextManagerVariableType(PyClass contextManager,
                                                                PyExpression withExpression,
                                                                TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getContextManagerVariableType(contextManager, withExpression, customContext);
        });
    }

    public @Nullable PyType getContextManagerVariableType(PyClass manager, PyExpression expression, Context context) {
        return null;
    }

    @Override
    public final @Nullable PyType getCallableType(PyCallable callable, TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getCallableType(callable, customContext);
        });
    }

    public @Nullable PyType getCallableType(PyCallable callable, Context context) {
        return null;
    }

    @Override
    public final @Nullable PyType getGenericType(PyClass cls, TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getGenericType(cls, customContext);
        });
    }

    public @Nullable PyType getGenericType(PyClass cls, Context context) {
        return null;
    }

    @Override
    public final Map<PyType, PyType> getGenericSubstitutions(PyClass cls, TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return getGenericSubstitutions(cls, customContext);
        });
    }

    public @Nullable Map<PyType, PyType> getGenericSubstitutions(PyClass cls, Context context) {
        return null;
    }

    @Override
    public final @Nullable Ref<@Nullable PyType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                          PyExpression callee,
                                                                          TypeEvalContext context) {
        return withCustomContext(context, customContext -> {
            return prepareCalleeTypeForCall(type, callee, customContext);
        });
    }

    public @Nullable Ref<PyType> prepareCalleeTypeForCall(@Nullable PyType type, PyExpression callee, Context context) {
        return null;
    }

    protected abstract <T> T withCustomContext(TypeEvalContext context, Function<Context, T> delegate);
}
