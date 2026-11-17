// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Class is used for representing TypeGuard and TypeIs behavior
 */
public class PyNarrowedType extends PyClassTypeImpl {
    private final @Nullable String qname;
    private final @Nullable PyCallExpression original;
    private final boolean negated;
    private final boolean typeIs;
    private final @Nullable PyType narrowedType;

    private PyNarrowedType(PyClass pyClass,
                           @Nullable String qname,
                           @Nullable PyCallExpression original,
                           boolean negated,
                           boolean typeIs,
                           @Nullable PyType narrowedType) {
        super(pyClass, false);
        this.qname = qname;
        this.original = original;
        this.negated = negated;
        this.typeIs = typeIs;
        this.narrowedType = narrowedType;
    }

    public @Nullable String getQname() {
        return qname;
    }

    public @Nullable PyCallExpression getOriginal() {
        return original;
    }

    public boolean getNegated() {
        return negated;
    }

    public boolean getTypeIs() {
        return typeIs;
    }

    public @Nullable PyType getNarrowedType() {
        return narrowedType;
    }

    public PyNarrowedType negate() {
        return new PyNarrowedType(getPyClass(), qname, original, !negated, typeIs, narrowedType);
    }

    public PyNarrowedType bind(PyCallExpression callExpression, String name) {
        return new PyNarrowedType(getPyClass(), name, callExpression, negated, typeIs, narrowedType);
    }

    public PyNarrowedType substitute(@Nullable PyType narrowedTypeSubstitution) {
        return new PyNarrowedType(getPyClass(), qname, original, negated, typeIs, narrowedTypeSubstitution);
    }

    /**
     * A type is considered bound if it has an associated original call site expression,
     * indicating that it is valid only within the call-site scope.
     */
    public boolean isBound() {
        return original != null;
    }

    @Override
    public String toString() {
        return "PyNarrowedType: " + (narrowedType != null ? narrowedType.getName() : "Any");
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        PyNarrowedType other = (PyNarrowedType)o;

        if (negated != other.negated) {
            return false;
        }
        if (typeIs != other.typeIs) {
            return false;
        }
        if (!Objects.equals(qname, other.qname)) {
            return false;
        }
        if (!Objects.equals(original, other.original)) {
            return false;
        }
        if (!Objects.equals(narrowedType, other.narrowedType)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Boolean.hashCode(negated);
        result = 31 * result + Boolean.hashCode(typeIs);
        result = 31 * result + (qname != null ? qname.hashCode() : 0);
        result = 31 * result + (original != null ? original.hashCode() : 0);
        result = 31 * result + (narrowedType != null ? narrowedType.hashCode() : 0);
        return result;
    }

    public static @Nullable PyNarrowedType create(PyElement anchor, boolean typeIs, @Nullable PyType returnType) {
        PyClass pyClass = PyBuiltinCache.getInstance(anchor).getClass("bool");
        if (pyClass == null) {
            return null;
        }
        return new PyNarrowedType(pyClass, null, null, false, typeIs, returnType);
    }

    public static @Nullable PyType bindIfNeeded(@Nullable PyType type, @Nullable PyElement callSite) {
        if (type instanceof PyNarrowedType narrowedType && callSite instanceof PyCallExpression callExpression) {
            PyExpression referenceExpression = ArrayUtil.getFirstElement(callExpression.getArguments());
            if (referenceExpression instanceof PyReferenceExpression reference) {
                QualifiedName qname = reference.asQualifiedName();
                if (qname != null) {
                    return narrowedType.bind(callExpression, qname.toString());
                }
            }
        }
        return type;
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyNarrowedType(this);
        }
        return visitor.visitPyClassType(this);
    }
}
