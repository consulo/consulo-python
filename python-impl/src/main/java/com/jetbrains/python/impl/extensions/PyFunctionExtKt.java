// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import org.jspecify.annotations.Nullable;

/**
 * Top-level declarations of {@code PyFunctionExt.kt}.
 */
public final class PyFunctionExtKt {
    private PyFunctionExtKt() {
    }

    public static @Nullable PyParameter getParameter(PyFunction function, FunctionParameter paramToSearch) {
        PyParameterList parameterList = function.getParameterList();
        int position = paramToSearch.getPosition();
        String name = paramToSearch.getName();
        if (name != null) {
            PyParameter parameter = parameterList.findParameterByName(name);
            if (parameter != null) {
                return parameter;
            }
        }
        if (position != FunctionParameter.POSITION_NOT_SUPPORTED) {
            boolean hasSelf = function.getContainingClass() != null;
            PyParameter[] parameters = parameterList.getParameters();
            int index = hasSelf ? position + 1 : position;
            return index >= 0 && index < parameters.length ? parameters[index] : null;
        }
        return null;
    }
}
