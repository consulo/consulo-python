// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jspecify.annotations.Nullable;

/**
 * Similarly to {@link consulo.language.psi.PsiElementVisitor}, implements double dispatching for the {@link PyType} hierarchy.
 * <p>
 * <p>
 * Because the "unknown" type is historically represented as {@code null} in the type system, {@code PyTypeVisitor.visitPyType(type, visitor)}
 * should be used instead of direct {@code type.acceptTypeVisitor(visitor)} to properly account for possible {@code null} values.
 * <p>
 * <p>
 * This class gives access only to the types declared in the <tt>intellij.python.psi</tt> module.
 * Most actual implementations should extend {@code PyTypeVisitorExt}.
 * <p>
 * <p>
 * There are helper {@code PyRecursiveTypeVisitor} and {@code PyCloningTypeVisitor} for recursive type
 * traversal and deep cloning of a type respectively.
 *
 * @see PyType#acceptTypeVisitor
 * @see #visit
 * @see #visitUnknownType
 */
public abstract class PyTypeVisitor<T> {
    public @Nullable T visitPyType(PyType type) {
        return null;
    }

    public @Nullable T visitPyClassType(PyClassType classType) {
        return visitPyClassLikeType(classType);
    }

    public @Nullable T visitPyClassLikeType(PyClassLikeType classLikeType) {
        // Don't treat PyClassLikeType as PyCallableType. It's usually not what a visitor's user expects.
        return visitPyType(classLikeType);
    }

    public @Nullable T visitPyFunctionType(PyFunctionType functionType) {
        return visitPyCallableType(functionType);
    }

    public @Nullable T visitPyCallableType(PyCallableType callableType) {
        return visitPyType(callableType);
    }

    public @Nullable T visitPyTypeVarType(PyTypeVarType typeVarType) {
        return visitPyTypeParameterType(typeVarType);
    }

    public @Nullable T visitPyTypeVarTupleType(PyTypeVarTupleType typeVarTupleType) {
        return visitPyTypeParameterType(typeVarTupleType);
    }

    public @Nullable T visitPyTypeParameterType(PyTypeParameterType typeParameterType) {
        return visitPyType(typeParameterType);
    }

    public @Nullable T visitPyUnpackedTupleType(PyUnpackedTupleType unpackedTupleType) {
        return visitPyType(unpackedTupleType);
    }

    public @Nullable T visitPyCallableParameterListType(PyCallableParameterListType callableParameterListType) {
        return visitPyType(callableParameterListType);
    }

    public @Nullable T visitPyNeverType(PyNeverType neverType) {
        return visitPyType(neverType);
    }

    public @Nullable T visitPyTopType(PyTopType topType) {
        return visitPyType(topType);
    }

    public @Nullable T visitAnyType() {
        return PyAnyType.isEnabled() ? visitPyType(PyAnyType.Any.INSTANCE) : null;
    }

    public @Nullable T visitUnknownType() {
        return PyAnyType.isEnabled() ? visitPyType(PyAnyType.Unknown.INSTANCE) : null;
    }

    /**
     * Use this method instead of {@link PyType#acceptTypeVisitor} to take into account
     * "unknown" {@code null} types, for which {@link #visitUnknownType} should be called.
     */
    public static <T> @Nullable T visit(@Nullable PyType type, PyTypeVisitor<T> visitor) {
        return type == null ? visitor.visitUnknownType() : type.acceptTypeVisitor(visitor);
    }
}
