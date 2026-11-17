// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A type instances of which can possibly be called. For example, a class definition can be called, and the result of a call is a class
 * instance.
 */
public interface PyCallableType extends PyType {
    /**
     * Returns true if the type is callable.
     */
    default boolean isCallable() {
        return true;
    }

    default @Nullable List<PyTypeParameterType> getTypeParameters(TypeEvalContext context) {
        return null;
    }

    /**
     * Returns the return type of a function independent of a call site.
     * <p>
     * For example, it may return a generic type.
     */
    @Nullable
    PyType getReturnType(TypeEvalContext context);

    /**
     * Returns the type which is the result of calling an instance of this type.
     *
     * @param callSite  the PSI element that represents the call, e.g. a call expression or an operator expression, or
     *                  {@code null} for calls such as an invocation of a property getter or an application of a decorator
     *                  to a function.
     * @param arguments the arguments the call type is evaluated with.
     */
    @Nullable
    PyType getCallType(TypeEvalContext context, @Nullable PyElement callSite, List<PyCallableArgument> arguments);

    /**
     * Returns the list of parameter types.
     *
     * @return list of parameter info null if not applicable.
     */
    default @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        return null;
    }

    /**
     * Returns the list of parameter types with keyword containers carrying {@code Unpack[TypedDict]}
     * expanded into individual keyword parameters derived from the TypedDict fields.
     *
     * @see PyCallableParameterListType#getUnpackedParameters
     */
    default @Nullable List<PyCallableParameter> getUnpackedParameters(TypeEvalContext context) {
        return getParameters(context);
    }

    /**
     * Returns the parameters type as a variadic type.
     * This method provides a unified way to handle different forms of callable parameters:
     * <ul>
     * <li>{@link PyCallableParameterListType} for regular parameter lists</li>
     * <li>{@code PyParamSpecType} for a single ParamSpec parameter type <code>Callable[P, R]</code></li>
     * <li>{@code PyConcatenateType} for a single Concatenate type <code>Callable[Concatenate[T1, T2, P], R]</code></li>
     * </ul>
     *
     * @return the parameters type, or null if not applicable
     */
    default @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        // TODO: introduce a form for gradual variadic type to replace null here
        return null;
    }

    @Override
    default @Nullable String getName() {
        TypeEvalContext context = TypeEvalContext.codeInsightFallback(null);
        List<PyCallableParameter> parameters = getParameters(context);
        PyType returnType = getReturnType(context);
        return String.format(
            "(%s) -> %s",
            parameters != null ?
                StringUtil.join(
                    parameters,
                    param -> {
                        if (param != null) {
                            StringBuilder builder = new StringBuilder();
                            String name = param.getName();
                            PyType type = param.getType(context);
                            if (name != null) {
                                builder.append(name);
                                builder.append(": ");
                            }
                            builder.append(type != null ? type.getName() : PyNames.ANY_TYPE);
                            return builder.toString();
                        }
                        return PyNames.ANY_TYPE;
                    },
                    ", "
                ) :
                "...",
            returnType != null ? returnType.getName() : PyNames.ANY_TYPE
        );
    }

    default @Nullable PyCallable getCallable() {
        return null;
    }

    default PyCallableType dropSelf(TypeEvalContext context) {
        return this;
    }

    default PyFunction.@Nullable Modifier getModifier() {
        return null;
    }

    @Override
    default <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyCallableType(this);
    }
}
