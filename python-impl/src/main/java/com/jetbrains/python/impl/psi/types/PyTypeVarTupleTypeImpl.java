// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.ref.Ref;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.PyVariance;

public final class PyTypeVarTupleTypeImpl implements PyTypeVarTupleType {
    private final String myName;
    private final @Nullable PyQualifiedNameOwner myScopeOwner;
    private final @Nullable PyType myBound;
    private final @Nullable Ref<PyPositionalVariadicType> myDefaultType;
    private final PyVariance myVariance;
    private final @Nullable PyQualifiedNameOwner myDeclarationElement;

    public PyTypeVarTupleTypeImpl(String name) {
        this(name, null, null, null, PyVariance.INVARIANT, null);
    }

    private PyTypeVarTupleTypeImpl(String name,
                                   @Nullable PyQualifiedNameOwner declarationElement,
                                   @Nullable PyType bound,
                                   @Nullable Ref<PyPositionalVariadicType> defaultType,
                                   PyVariance variance,
                                   @Nullable PyQualifiedNameOwner scopeOwner) {
        myName = name;
        myDeclarationElement = declarationElement;
        myBound = bound;
        myDefaultType = defaultType;
        myVariance = variance;
        myScopeOwner = scopeOwner;
    }

    public PyTypeVarTupleTypeImpl withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
        return new PyTypeVarTupleTypeImpl(myName, myDeclarationElement, myBound, myDefaultType, myVariance, scopeOwner);
    }

    public PyTypeVarTupleTypeImpl withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
        return new PyTypeVarTupleTypeImpl(myName, declarationElement, myBound, myDefaultType, myVariance, myScopeOwner);
    }

    public PyTypeVarTupleTypeImpl withBound(@Nullable PyType bound) {
        return new PyTypeVarTupleTypeImpl(myName, myDeclarationElement, bound, myDefaultType, myVariance, myScopeOwner);
    }

    public PyTypeVarTupleTypeImpl withDefaultType(@Nullable Ref<PyPositionalVariadicType> defaultType) {
        return new PyTypeVarTupleTypeImpl(myName, myDeclarationElement, myBound, defaultType, myVariance, myScopeOwner);
    }

    public PyTypeVarTupleTypeImpl withVariance(PyVariance variance) {
        return new PyTypeVarTupleTypeImpl(myName, myDeclarationElement, myBound, myDefaultType, variance, myScopeOwner);
    }

    @Override
    public String getName() {
        return "*" + myName;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getScopeOwner() {
        return myScopeOwner;
    }

    @Override
    public @Nullable PyType getBound() {
        return myBound;
    }

    @Override
    public @Nullable Ref<PyPositionalVariadicType> getDefaultType() {
        return myDefaultType;
    }

    @Override
    public PyVariance getVariance() {
        return myVariance;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getDeclarationElement() {
        return myDeclarationElement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyTypeVarTupleTypeImpl type = (PyTypeVarTupleTypeImpl)o;
        return myName.equals(type.myName) && Objects.equals(getScopeOwner(), type.getScopeOwner());
    }

    @Override
    public int hashCode() {
        return Objects.hash(myName, myScopeOwner);
    }

    @Override
    public String toString() {
        String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
        return "PyTypeVarTupleType: " + (scopeName != null ? scopeName + ":" : "") + myName;
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyTypeVarTupleType(this);
    }
}
