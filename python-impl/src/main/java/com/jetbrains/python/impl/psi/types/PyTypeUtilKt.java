// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.isObjectClass;

public final class PyTypeUtilKt {
    private PyTypeUtilKt() {
    }

    public static boolean isAnyOrUnknown(@Nullable PyType type) {
        PyAnyType.validate(type);
        return PyAnyType.isEnabled() ? type instanceof PyAnyType : type == null;
    }

    public static boolean isAny(@Nullable PyType type) {
        PyAnyType.validate(type);
        return PyAnyType.isEnabled() ? type instanceof PyAnyType.Any : type == null;
    }

    public static boolean isUnknown(@Nullable PyType type) {
        PyAnyType.validate(type);
        return PyAnyType.isEnabled() ? type instanceof PyAnyType.Unknown : type == null;
    }

    public static boolean isObject(@Nullable PyType type) {
        return type instanceof PyClassType classType && isObjectClass(classType.getPyClass());
    }

    public static @Nullable PyType getLiteralType(PyExpression expression, TypeEvalContext context) {
        return PyLiteralType.getLiteralType(expression, context);
    }

    /**
     * Widens literal types within a tuple type.
     * When a tuple appears nested in a non-tuple container type (e.g., {@code list[tuple[Literal[1], Literal["a"]]]}),
     * its literal element types should be widened to their base types (e.g., {@code list[tuple[int, str]]}).
     */
    public static @Nullable PyType widenTupleLiterals(@Nullable PyType type) {
        if (!(type instanceof PyTupleType tupleType) || type instanceof PyNamedTupleType) {
            return type;
        }
        List<@Nullable PyType> widenedElements = new ArrayList<>();
        for (PyType it : tupleType.getElementTypes()) {
            widenedElements.add(PyTypeUtil.widenLiteralAndNumeric(it));
        }
        return new PyTupleType(tupleType.getPyClass(), widenedElements, tupleType.isHomogeneous());
    }
}
