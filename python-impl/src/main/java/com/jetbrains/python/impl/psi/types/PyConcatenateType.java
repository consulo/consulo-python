// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Type of typing.Concatenate to store corresponding first type and parameter specification
 */
public class PyConcatenateType implements PyCallableParameterVariadicType {
    private final List<@Nullable PyType> myFirstTypes;
    private final @Nullable PyParamSpecType myParamSpec;
    private final String myName;

    public PyConcatenateType(List<@Nullable PyType> firstTypes, @Nullable PyParamSpecType paramSpec) {
        myFirstTypes = firstTypes;
        myParamSpec = paramSpec;
        myName = "Concatenate(" + firstTypes.stream().map(it -> it != null ? it.getName() : "Any").collect(Collectors.joining(", ")) + ", " +
            (paramSpec != null ? paramSpec.getName() : "...") + ")";
    }

    public List<@Nullable PyType> getFirstTypes() {
        return myFirstTypes;
    }

    public @Nullable PyParamSpecType getParamSpec() {
        return myParamSpec;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyConcatenateType(this);
        }
        return visitor.visitPyType(this);
    }
}
