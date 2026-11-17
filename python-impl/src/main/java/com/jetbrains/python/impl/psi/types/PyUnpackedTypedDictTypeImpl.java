// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PyUnpackedTypedDictTypeImpl implements PyUnpackedTypedDictType {
    private final PyTypedDictType typedDictType;
    private final String name;

    public PyUnpackedTypedDictTypeImpl(PyTypedDictType typedDictType) {
        this.typedDictType = typedDictType;
        this.name = "Unpack[" + typedDictType.getName() + "]";
    }

    @Override
    public PyTypedDictType getTypedDictType() {
        return typedDictType;
    }

    @Override
    public List<PyCallableParameter> getUnpackedParameters(TypeEvalContext context) {
        List<PyCallableParameter> parameters = typedDictType.toClass().getParameters(context);
        return parameters != null ? parameters : Collections.emptyList();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PyUnpackedTypedDictTypeImpl otherType)) {
            return false;
        }
        return typedDictType.equals(otherType.typedDictType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typedDictType);
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyUnpackedTypedDictType(this);
        }
        return visitor.visitPyType(this);
    }
}
