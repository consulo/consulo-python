// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import org.jspecify.annotations.Nullable;

public abstract class PyTypeVisitorExt<T> extends PyTypeVisitor<T> {
    public @Nullable T visitPyLiteralType(PyLiteralType literalType) {
        return visitPyClassType(literalType);
    }

    public @Nullable T visitPyLiteralStringType(PyLiteralStringType literalStringType) {
        return visitPyClassType(literalStringType);
    }

    public @Nullable T visitPyModuleType(PyModuleType moduleType) {
        return visitPyType(moduleType);
    }

    public @Nullable T visitPyParamSpecType(PyParamSpecType paramSpecType) {
        return visitPyTypeParameterType(paramSpecType);
    }

    /**
     * @deprecated use visitPyClassType instead
     */
    @Deprecated
    public @Nullable T visitPyGenericType(PyClassType genericType) {
        return visitPyClassType(genericType);
    }

    public @Nullable T visitPyTupleType(PyTupleType tupleType) {
        return visitPyClassType(tupleType);
    }

    public @Nullable T visitPyNamedTupleType(PyNamedTupleType namedTupleType) {
        return visitPyClassType(namedTupleType);
    }

    public @Nullable T visitPySelfType(PySelfType selfType) {
        return visitPyTypeParameterType(selfType);
    }

    public @Nullable T visitPyTypedDictType(PyTypedDictType typedDictType) {
        return visitPyClassType(typedDictType);
    }

    public @Nullable T visitPyUnionType(PyUnionType unionType) {
        return visitPyCompositeType(unionType);
    }

    public @Nullable T visitPyUnsafeUnionType(PyUnsafeUnionType unsafeUnionType) {
        return visitPyCompositeType(unsafeUnionType);
    }

    public @Nullable T visitPyIntersectionType(PyIntersectionType intersectionType) {
        return visitPyCompositeType(intersectionType);
    }

    public @Nullable T visitPyCompositeType(PyCompositeType compositeType) {
        return visitPyType(compositeType);
    }

    public @Nullable T visitPyTypingNewType(PyTypingNewType typingNewType) {
        return visitPyClassType(typingNewType);
    }

    public @Nullable T visitPyNarrowedType(PyNarrowedType narrowedType) {
        return visitPyType(narrowedType);
    }

    public @Nullable T visitPyTypeFormType(PyTypeFormType typeFormType) {
        return visitPyClassType(typeFormType);
    }

    public @Nullable T visitPyConcatenateType(PyConcatenateType concatenateType) {
        return visitPyType(concatenateType);
    }

    public @Nullable T visitPyOverloadType(PyOverloadType overloadType) {
        return visitPyType(overloadType);
    }

    public @Nullable T visitPyUnpackedTypedDictType(PyUnpackedTypedDictType unpackedTypedDictType) {
        return visitPyType(unpackedTypedDictType);
    }
}
