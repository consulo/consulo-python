// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

/**
 * JB: the top-level functions of {@code PyTypeInferenceCspFactory.kt}.
 */
public final class PyTypeInferenceCspFactoryKt {
    private PyTypeInferenceCspFactoryKt() {
    }

    public static @Nullable PyType normalizeType(@Nullable PyType type, TypeEvalContext context) {
        PyType normalizedType = type;
        if (type instanceof PyClassType classType && !classType.isParameterized()) {
            // convert raw types to generic types
            // keep the type variables for the CSP to be computed
            PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), classType.isDefinition(), context);
            normalizedType = genericDefinitionType != null ? genericDefinitionType : type;
        }
        if (type instanceof PyTupleType tupleType && tupleType.getElementTypes().isEmpty()) {
            // treat this as `tuple[()]` and normalize it to `tuple[Never, ...]`
            normalizedType = PyTupleType.createHomogeneous(tupleType.getDeclarationElement(), PyNeverType.NEVER);
        }
        return normalizedType;
    }

    public static @Nullable PyType getNarrowedType(TypeEvalContext context, PyExpression element) {
        PyType literalType = PyLiteralType.getLiteralType(element, context);
        return literalType != null ? literalType : context.getType(element);
    }
}
