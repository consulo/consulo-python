// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;


@SuppressWarnings("deprecation")
public class PyTupleType extends PyClassTypeImpl implements PyCollectionType {

    private final PyUnpackedTupleType myUnpackedTupleType;

    public static @Nullable PyTupleType create(PsiElement anchor, List<? extends PyType> elementTypes) {
        PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
        if (tuple != null) {
            return new PyTupleType(tuple, elementTypes, false);
        }
        return null;
    }

    public static @Nullable PyTupleType createHomogeneous(PsiElement anchor, @Nullable PyType elementType) {
        PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
        if (tuple != null) {
            return new PyTupleType(tuple, Collections.singletonList(elementType), true);
        }
        return null;
    }

    public PyTupleType(PyClass tupleClass, List<? extends PyType> elementTypes, boolean homogeneous) {
        this(tupleClass, elementTypes, homogeneous, false);
    }

    protected PyTupleType(PyClass tupleClass,
                          List<? extends PyType> elementTypes,
                          boolean homogeneous,
                          boolean isDefinition) {
        this(tupleClass, elementTypes, homogeneous, isDefinition, Collections.emptyList());
    }

    /**
     * @param classTypeArguments the type arguments of a generic subclass of {@code tuple}, such as a generic named tuple.
     *                           A plain tuple has none, because its element types are its type arguments.
     */
    protected PyTupleType(PyClass tupleClass,
                          List<? extends PyType> elementTypes,
                          boolean homogeneous,
                          boolean isDefinition,
                          List<? extends PyType> classTypeArguments) {
        super(tupleClass, isDefinition, classTypeArguments);
        myUnpackedTupleType = new PyUnpackedTupleTypeImpl(elementTypes, homogeneous);
    }

    @Override
    public String getName() {
        if (myUnpackedTupleType.isUnbound()) {
            return "(" + (getTypeName(getIteratedItemType())) + ", ...)";
        }
        var elementTypes = myUnpackedTupleType.getElementTypes();
        var suffix = elementTypes.size() == 1 ? ",)" : ")";
        return "(" + StringUtil.join(elementTypes, PyTupleType::getTypeName, ", ") + suffix;
    }

    private static @Nullable String getTypeName(@Nullable PyType type) {
        return type == null ? PyNames.ANY_TYPE : type.getName();
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    /**
     * Access elements by zero-based index.
     *
     * @param index an index of item
     * @return type of item
     */
    public @Nullable PyType getElementType(int index) {
        if (isHomogeneous()) {
            return getIteratedItemType();
        }
        List<PyType> elementTypes = myUnpackedTupleType.getElementTypes();
        return index >= 0 && index < elementTypes.size() ? elementTypes.get(index) : PyAnyType.getUnknown();
    }

    public int getElementCount() {
        return isHomogeneous() ? -1 : myUnpackedTupleType.getElementTypes().size();
    }

    public boolean isHomogeneous() {
        return myUnpackedTupleType.isUnbound();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        PyTupleType type = (PyTupleType)o;
        return Objects.equals(myUnpackedTupleType, type.myUnpackedTupleType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), myUnpackedTupleType);
    }

    @Override
    public List<PyType> getTypeArguments() {
        return myUnpackedTupleType.getElementTypes();
    }

    /**
     * The element types of the tuple. For {@code tuple[int, ...]} it is the single repeated type.
     */
    @Override
    public List<PyType> getElementTypes() {
        return myUnpackedTupleType.getElementTypes();
    }

    @Override
    public @Nullable PyType getIteratedItemType() {
        List<PyType> types = myUnpackedTupleType.getElementTypes();
        List<PyType> unpackedTypes = ContainerUtil.map(types, type -> {
            if (type instanceof PyUnpackedTupleType unpackedTupleType) {
                assert unpackedTupleType.isUnbound();
                return unpackedTupleType.getElementTypes().get(0);
            }
            else {
                return type;
            }
        });
        return PyUnionType.unionOrNever(unpackedTypes);
    }

    @Override
    public String toString() {
        return "PyTupleType: " + getName();
    }

    public PyUnpackedTupleType asUnpackedTupleType() {
        return myUnpackedTupleType;
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyTupleType(this);
        }
        return visitor.visitPyType(this);
    }
}
