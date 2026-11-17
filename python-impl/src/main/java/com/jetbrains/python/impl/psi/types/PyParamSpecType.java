// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.ref.Ref;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.PyVariance;

/**
 * Represents a type parameter substituted with a parameter list of a callable as described in
 * <a href="https://peps.python.org/pep-0612/">PEP 612 – Parameter Specification Variables</a>.
 * <p>
 * Declared with either {@code typing.ParamSpec} instantiation or {@code **P} syntax.
 * Concrete instantiations of such a parameter are either {@link PyCallableParameterListType} or {@link PyConcatenateType}.
 *
 * @see PyCallableParameterListType
 * @see PyConcatenateType
 */
public final class PyParamSpecType implements PyTypeParameterType, PyCallableParameterVariadicType {
    private final String myName;
    private final @Nullable PyQualifiedNameOwner myDeclarationElement;
    private final @Nullable PyType myBound;
    private final @Nullable Ref<PyType> myDefaultType;
    private final PyVariance myVariance;
    private final @Nullable PyQualifiedNameOwner myScopeOwner;

    public PyParamSpecType(String name) {
        this(name, null, null, null, PyVariance.INVARIANT, null);
    }

    private PyParamSpecType(String name,
                            @Nullable PyQualifiedNameOwner declarationElement,
                            @Nullable PyType bound,
                            @Nullable Ref<PyType> defaultType,
                            PyVariance variance,
                            @Nullable PyQualifiedNameOwner scopeOwner) {
        myName = name;
        myDeclarationElement = declarationElement;
        myBound = bound;
        myDefaultType = defaultType;
        myVariance = variance;
        myScopeOwner = scopeOwner;
    }

    public PyParamSpecType withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
        return new PyParamSpecType(myName, declarationElement, myBound, myDefaultType, myVariance, myScopeOwner);
    }

    public PyParamSpecType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
        return new PyParamSpecType(myName, myDeclarationElement, myBound, myDefaultType, myVariance, scopeOwner);
    }

    public PyParamSpecType withBound(@Nullable PyType bound) {
        return new PyParamSpecType(myName, myDeclarationElement, bound, myDefaultType, myVariance, myScopeOwner);
    }

    public PyParamSpecType withDefaultType(@Nullable Ref<PyType> defaultType) {
        return new PyParamSpecType(myName, myDeclarationElement, myBound, defaultType, myVariance, myScopeOwner);
    }

    public PyParamSpecType withVariance(PyVariance variance) {
        return new PyParamSpecType(myName, myDeclarationElement, myBound, myDefaultType, variance, myScopeOwner);
    }

    @Override
    public @Nullable PyQualifiedNameOwner getDeclarationElement() {
        return myDeclarationElement;
    }

    @Override
    public String getName() {
        return "**" + myName;
    }

    @Override
    public @Nullable PyType getBound() {
        return myBound;
    }

    @Override
    public PyVariance getVariance() {
        return myVariance;
    }

    @Override
    public String toString() {
        String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
        return "PyParamSpecType: " + (scopeName != null ? scopeName + ":" : "") + myName;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getScopeOwner() {
        return myScopeOwner;
    }

    @Override
    public @Nullable Ref<PyType> getDefaultType() {
        return myDefaultType;
    }

    public String getVariableName() {
        return myName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyParamSpecType type = (PyParamSpecType)o;
        return myName.equals(type.myName) && Objects.equals(myScopeOwner, type.myScopeOwner);
    }

    @Override
    public int hashCode() {
        return myName.hashCode();
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyParamSpecType(this);
        }
        return visitor.visitPyTypeParameterType(this);
    }
}
