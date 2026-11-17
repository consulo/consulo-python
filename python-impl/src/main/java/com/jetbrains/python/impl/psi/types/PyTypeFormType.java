// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

/**
 * The PEP 747 {@code typing.TypeForm} special form: a value that denotes the type {@link #getRepresentedType()}.
 * <p>
 * Backed by the builtin {@code type} class; matching and inference semantics live in {@link PyTypeChecker} and
 * {@link PyTypeInferenceCspFactory}.
 */
public class PyTypeFormType extends PyClassTypeImpl {
    private final @Nullable PyType representedType;

    private PyTypeFormType(PyClass pyClass, @Nullable PyType representedType) {
        super(pyClass, false);
        this.representedType = representedType;
    }

    public @Nullable PyType getRepresentedType() {
        return representedType;
    }

    public PyTypeFormType substitute(@Nullable PyType representedTypeSubstitution) {
        return new PyTypeFormType(getPyClass(), representedTypeSubstitution);
    }

    // Preserve identity through conversions instead of collapsing to the backing `type` class.
    @Override
    public PyClassType toInstance() {
        return this;
    }

    @Override
    public PyClassType toClass() {
        return this;
    }

    @Override
    public String toString() {
        return "PyTypeFormType: " + (representedType != null ? representedType.getName() : "Any");
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

        PyTypeFormType other = (PyTypeFormType)o;

        return Objects.equals(representedType, other.representedType);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (representedType != null ? representedType.hashCode() : 0);
        return result;
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyTypeFormType(this);
        }
        return visitor.visitPyClassType(this);
    }

    public static @Nullable PyTypeFormType create(PsiElement anchor, @Nullable PyType representedType) {
        PyClass pyClass = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TYPE);
        if (pyClass == null) {
            return null;
        }
        return new PyTypeFormType(pyClass, representedType);
    }

    // Erased runtime containers of `X | Y` value expressions; ignored when reading a union's represented type.
    private static final Set<String> RUNTIME_TYPE_FORM_CONTAINERS = Set.of("types.UnionType", "types.GenericAlias");

    /**
     * The type {@code type} denotes as a type-expression value ({@code type[int]} -> {@code int}), or {@code null} if it is
     * not a valid type expression.
     */
    public static @Nullable PyType representedTypeOf(@Nullable PyType type) {
        if (type instanceof PyTypeFormType typeFormType) {
            return typeFormType.getRepresentedType();
        }
        // `None` is both a value and a type expression denoting `NoneType`.
        if (isNoneType(type)) {
            return type;
        }
        if (type instanceof PyInstantiableType<?> instantiableType && instantiableType.isDefinition()) {
            return instantiableType.toInstance();
        }
        if (type instanceof PyUnionType unionType) {
            List<@Nullable PyType> represented = new ArrayList<>();
            for (PyType member : unionType.getMembers()) {
                if (member instanceof PyInstantiableType<?> instantiableMember && instantiableMember.isDefinition()) {
                    represented.add(instantiableMember.toInstance());
                }
                else if (isNoneType(member)) {
                    represented.add(member);
                }
                else if (member instanceof PyClassType classMember && RUNTIME_TYPE_FORM_CONTAINERS.contains(classMember.getClassQName())) {
                    // erased container, ignore
                }
                else {
                    return null;
                }
            }
            return represented.isEmpty() ? null : PyUnionType.unionOrUnknown(represented);
        }
        return null;
    }
}
