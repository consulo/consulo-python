// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.PyVariance;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public final class PyTypeVarTypeImpl implements PyTypeVarType {
    private final String myName;
    private final List<@Nullable PyType> myConstraints;
    private final @Nullable PyType myBound;
    private final @Nullable Ref<PyType> myDefaultType;
    private final PyVariance myVariance;
    private final boolean myIsDefinition;
    private final @Nullable PyQualifiedNameOwner myDeclarationElement;
    private final @Nullable PyQualifiedNameOwner myScopeOwner;

    public PyTypeVarTypeImpl(String name, @Nullable PyType bound) {
        this(name, List.of(), bound, null, PyVariance.INVARIANT);
    }

    /** @deprecated use {@link #PyTypeVarTypeImpl(String, List, PyType, Ref, PyVariance)} */
    @Deprecated
    public PyTypeVarTypeImpl(String name,
                             List<@Nullable PyType> constraints,
                             @Nullable PyType bound,
                             @Nullable Ref<PyType> defaultType,
                             Variance variance) {
        this(name, constraints, bound, defaultType, variance.toPyVariance());
    }

    public PyTypeVarTypeImpl(String name,
                             List<@Nullable PyType> constraints,
                             @Nullable PyType bound,
                             @Nullable Ref<PyType> defaultType,
                             PyVariance variance) {
        this(name, constraints, bound, defaultType, variance, false, null, null);
    }

    /** @deprecated use {@link #PyTypeVarTypeImpl(String, List, PyType, Ref, PyVariance, boolean, PyQualifiedNameOwner, PyQualifiedNameOwner)} */
    @Deprecated
    PyTypeVarTypeImpl(String name,
                      List<@Nullable PyType> constraints,
                      @Nullable PyType bound,
                      @Nullable Ref<PyType> defaultType,
                      Variance variance,
                      boolean isDefinition,
                      @Nullable PyQualifiedNameOwner declarationElement,
                      @Nullable PyQualifiedNameOwner scopeOwner) {
        this(name, constraints, bound, defaultType, variance.toPyVariance(), isDefinition, declarationElement, scopeOwner);
    }

    PyTypeVarTypeImpl(String name,
                      List<@Nullable PyType> constraints,
                      @Nullable PyType bound,
                      @Nullable Ref<PyType> defaultType,
                      PyVariance variance,
                      boolean isDefinition,
                      @Nullable PyQualifiedNameOwner declarationElement,
                      @Nullable PyQualifiedNameOwner scopeOwner) {
        myName = name;
        constraints.forEach(PyAnyType::validate);
        myConstraints = constraints;
        PyAnyType.validate(bound);
        myBound = bound;
        myDefaultType = defaultType;
        myVariance = variance;
        myIsDefinition = isDefinition;
        myDeclarationElement = declarationElement;
        myScopeOwner = scopeOwner;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getDeclarationElement() {
        return myDeclarationElement;
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        PyType bound = getBoundPromotedToClassObjectTypesIfNeeded();
        if (!isUnknown(bound)) {
            return bound.resolveMember(name, location, direction, resolveContext);
        }
        PyType defaultType = getDefaultTypePromotedToClassObjectTypesIfNeeded();
        if (defaultType != null) {
            return defaultType.resolveMember(name, location, direction, resolveContext);
        }
        return null;
    }

    @Override
    public Object [] getCompletionVariants(String completionPrefix,
                                                    PsiElement location,
                                                    ProcessingContext context) {
        PyType bound = getBoundPromotedToClassObjectTypesIfNeeded();
        if (!isUnknown(bound)) {
            return bound.getCompletionVariants(completionPrefix, location, context);
        }

        PyType defaultType = getDefaultTypePromotedToClassObjectTypesIfNeeded();
        if (defaultType != null) {
            return defaultType.getCompletionVariants(completionPrefix, location, context);
        }
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    private @Nullable PyType getBoundPromotedToClassObjectTypesIfNeeded() {
        PyType effectiveBound = myConstraints.isEmpty() ? myBound : PyUnionType.unionOrUnknown(myConstraints);
        return promoteToClassObjectIfNeeded(effectiveBound);
    }

    private @Nullable PyType getDefaultTypePromotedToClassObjectTypesIfNeeded() {
        PyType defaultType = myDefaultType != null ? myDefaultType.get() : null;
        return promoteToClassObjectIfNeeded(defaultType);
    }

    private @Nullable PyType promoteToClassObjectIfNeeded(PyType type) {
        if (myIsDefinition) {
            return PyTypeUtil.toStream(type)
                .map(t -> t instanceof PyInstantiableType ? ((PyInstantiableType<?>)t).toClass() : t)
                .collect(PyTypeUtil.toUnion(type));
        }
        return type;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyTypeVarTypeImpl type = (PyTypeVarTypeImpl)o;
        return myName.equals(type.myName) && myIsDefinition == type.isDefinition() && Objects.equals(myScopeOwner, type.myScopeOwner);
    }

    @Override
    public int hashCode() {
        return myName.hashCode();
    }

    @Override
    public String toString() {
        // A qualified name can be null e.g., for a local function
        String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
        return "PyTypeVarType: " + (scopeName != null ? scopeName + ":" : "") + getName();
    }

    @Override
    public List<@Nullable PyType> getConstraints() {
        return myConstraints;
    }

    @Override
    public @Nullable PyType getBound() {
        return myBound;
    }

    @Override
    public @Nullable PyType getEffectiveBound() {
        return myConstraints.isEmpty() ? myBound : PyUnionType.unionOrUnknown(myConstraints);
    }

    @Override
    public PyVariance getVariance() {
        return myVariance;
    }

    @Override
    public @Nullable Ref<PyType> getDefaultType() {
        return myDefaultType;
    }

    @Override
    public boolean isDefinition() {
        return myIsDefinition;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getScopeOwner() {
        return myScopeOwner;
    }

    public PyTypeVarTypeImpl withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
        return new PyTypeVarTypeImpl(getName(), getConstraints(), getBound(), getDefaultType(), getVariance(), isDefinition(),
                                     getDeclarationElement(), scopeOwner);
    }

    public PyTypeVarTypeImpl withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
        return new PyTypeVarTypeImpl(getName(), getConstraints(), getBound(), getDefaultType(), getVariance(), isDefinition(),
                                     declarationElement, getScopeOwner());
    }

    @Override
    public PyTypeVarType toInstance() {
        return myIsDefinition ? new PyTypeVarTypeImpl(myName, myConstraints, myBound, myDefaultType, myVariance, false, myDeclarationElement,
                                                      myScopeOwner) : this;
    }

    @Override
    public PyTypeVarType toClass() {
        return myIsDefinition
               ? this
               : new PyTypeVarTypeImpl(myName, myConstraints, myBound, myDefaultType, myVariance, true, myDeclarationElement, myScopeOwner);
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyTypeVarType(this);
    }
}
