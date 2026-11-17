// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;


@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyTypeProvider {
    ExtensionPointName<PyTypeProvider> EP_NAME = ExtensionPointName.create(PyTypeProvider.class);

    @Nullable
    PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context);

    @Nullable
    Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor);

    @Nullable
    Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context);

    @Nullable
    Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context);

    @Nullable
    Ref<PyType> getCallType(PyFunction function, PyElement callSite, TypeEvalContext context);

    @Nullable
    Ref<PyType> getCallType(PyFunction function, PyCallSiteExpression callSite, TypeEvalContext context);

    @Nullable
    PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context);

    @Nullable
    PyType getCallableType(PyCallable callable, TypeEvalContext context);

    /**
     * Returns a parameterized version of the class type.
     *
     * E.g. for class C(Generic[T], B[Tuple[T, str]]) it should return C[T].
     */
    @Nullable
    PyType getGenericType(PyClass cls, TypeEvalContext context);

    /**
     * Returns a map of generic substitutions for the base classes.
     *
     * E.g. for class C(Generic[T], B[Tuple[T, str]]) where B(Generic[V]) it should return {V: Tuple[T, str]}.
     */
    Map<PyType, PyType> getGenericSubstitutions(PyClass cls, TypeEvalContext context);

    /**
     * <p>
     * If callee type is a class type, it is replaced with class constructor or {@code __call__}
     * depending on whether it is a definition or an instance.
     * </p>
     * <p>
     * The type wrapped into the {@link Ref} must be a {@link PyCallableType} or a {@code PyOverloadType}.
     * </p>
     * <p>
     * If the {@code type} is provided, and it is desirable to stay with the provided {@code type}, please wrap it into {@link Ref}.
     * </p>
     * <p>
     * If code insight should be suppressed for the {@code call}, return {@code null} wrapped into the {@link Ref}.
     * </p>
     * <p>
     * Return {@code null} otherwise.
     * </p>
     */
    @Nullable
    Ref<@Nullable PyType> prepareCalleeTypeForCall(@Nullable PyType calleeType, PyExpression callee, TypeEvalContext context);

    @Nullable
    List<PyTypeMember> getMemberTypes(
        PyType type,
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext context
    );
}
