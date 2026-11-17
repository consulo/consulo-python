// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.StringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.ParamHelper;

import java.util.List;
import java.util.Objects;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;

public final class PyCallableParameterListTypeImpl implements PyCallableParameterListType {
    private final List<PyCallableParameter> myParameters;

    public PyCallableParameterListTypeImpl(List<PyCallableParameter> parameters) {
        myParameters = List.copyOf(parameters);
    }

    @Override
    public List<PyCallableParameter> getParameters() {
        return myParameters;
    }

    @Override
    public List<PyCallableParameter> getUnpackedParameters(TypeEvalContext context) {
        return ParamHelper.unpackContainerParameters(myParameters, context);
    }

    @Override
    public String getName() {
        TypeEvalContext context = TypeEvalContext.codeInsightFallback(null);
        return String.format("[%s]",
                             StringUtil.join(myParameters, param -> {
                                               PyType type = param.getType(context);
                                               return type != null ? type.getName() : PyNames.ANY_TYPE;
                                             },
                                             ", "));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyCallableParameterListTypeImpl type = (PyCallableParameterListTypeImpl)o;
        return Objects.equals(myParameters, type.myParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myParameters);
    }

    @Override
    public String toString() {
        return "PyCallableParameterListType: " + getName();
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyCallableParameterListType(this);
    }
}
