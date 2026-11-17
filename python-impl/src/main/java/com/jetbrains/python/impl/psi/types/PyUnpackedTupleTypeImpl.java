// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;


import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public final class PyUnpackedTupleTypeImpl implements PyUnpackedTupleType {
    public static final PyUnpackedTupleType UNSPECIFIED = new PyUnpackedTupleTypeImpl(Collections.singletonList(PyAnyType.getUnknown()), true);

    private final List<PyType> myElementTypes;
    private final boolean myIsHomogeneous;

    public PyUnpackedTupleTypeImpl(List<? extends PyType> elementTypes, boolean isUnbound) {
        elementTypes.forEach(PyAnyType::validate);
        if (isUnbound) {
            if (elementTypes.size() != 1) {
                throw new IllegalArgumentException("Unbounded unpacked tuple type can have only one type parameter");
            }
            if (elementTypes.get(0) instanceof PyPositionalVariadicType) {
                throw new IllegalArgumentException("Unbounded unpacked tuple type of a TypeVarTuple or another unpacked tuple type is now allowed");
            }
            myElementTypes = new ArrayList<>(elementTypes);
        }
        else {
            myElementTypes = unpackElementTypes(elementTypes).toList();
        }
        myIsHomogeneous = isUnbound;
    }

    private static Stream<PyType> unpackElementTypes(List<? extends PyType> types) {
        return types.stream().flatMap(type -> {
            if (type instanceof PyUnpackedTupleType unpackedTupleType && !unpackedTupleType.isUnbound()) {
                return unpackElementTypes(unpackedTupleType.getElementTypes());
            }
            else {
                return Stream.of(type);
            }
        });
    }

    public static PyUnpackedTupleType create(List<? extends PyType> elementTypes) {
        return new PyUnpackedTupleTypeImpl(elementTypes, false);
    }

    public static PyUnpackedTupleType createUnbound(@Nullable PyType type) {
        return new PyUnpackedTupleTypeImpl(Collections.singletonList(type), true);
    }

    @Override
    public String getName() {
        StringBuilder res = new StringBuilder("*tuple[");
        StringUtil.join(myElementTypes, type -> type != null ? type.getName() : "Any", ", ", res);
        if (isUnbound()) {
            res.append(", ...");
        }
        res.append("]");
        return res.toString();
    }

    @Override
    public List<PyType> getElementTypes() {
        return this == UNSPECIFIED
               ? Collections.singletonList(PyAnyType.getUnknown())
               : Collections.unmodifiableList(myElementTypes);
    }

    @Override
    public boolean isUnbound() {
        return myIsHomogeneous;
    }

    @Override
    public List<PyCallableParameter> asCallableParameters() {
        if (isUnbound()) {
            return List.of();
        }

        List<PyType> elementTypes = getElementTypes();
        if (elementTypes.isEmpty() || ContainerUtil.exists(elementTypes, type -> type instanceof PyTypeVarTupleType)) {
            return List.of();
        }

        int variadicIdx = (int)StreamEx.of(elementTypes)
            .indexOf(type -> type instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound())
            .orElse(-1);

        List<PyCallableParameter> result = new ArrayList<>();

        for (int i = 0; i < elementTypes.size(); i++) {
            if (i == variadicIdx) {
                PyUnpackedTupleType variadic = as(elementTypes.get(i), PyUnpackedTupleType.class);
                assert variadic != null;
                result.add(PyCallableParameterImpl.positionalContainerNonPsi(null, variadic.getElementTypes().getFirst()));
            }
            else {
                result.add(PyCallableParameterImpl.nonPsi("__p" + i, elementTypes.get(i)));
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyUnpackedTupleTypeImpl type = (PyUnpackedTupleTypeImpl)o;
        return myIsHomogeneous == type.myIsHomogeneous && Objects.equals(getElementTypes(), type.getElementTypes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getElementTypes(), myIsHomogeneous);
    }

    @Override
    public String toString() {
        return "PyUnpackedTupleType: " + getName();
    }

    public @Nullable PyTupleType asTupleType(PsiElement anchor) {
        if (isUnbound()) {
            return PyTupleType.createHomogeneous(anchor, getElementTypes().get(0));
        }
        else {
            return PyTupleType.create(anchor, getElementTypes());
        }
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyUnpackedTupleType(this);
    }
}
