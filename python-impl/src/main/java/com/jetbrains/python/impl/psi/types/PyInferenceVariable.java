// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Inference variables are meta-variables for types, i.e., they represent the unknown types searched for while solving a
 * constraint system.
 */
public class PyInferenceVariable implements PyType {
    private final PyTypeVarType typeVariable;
    private final SubstitutionsIdentifier substitutionsIdentifier;
    private final String name;

    public PyInferenceVariable(PyTypeVarType typeVariable, SubstitutionsIdentifier substitutionsIdentifier, String name) {
        this.typeVariable = typeVariable;
        this.substitutionsIdentifier = substitutionsIdentifier;
        this.name = name;
    }

    public PyTypeVarType getTypeVariable() {
        return typeVariable;
    }

    public SubstitutionsIdentifier getSubstitutionsIdentifier() {
        return substitutionsIdentifier;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public List<? extends RatedResolveResult> resolveMember(String name,
                                                            @Nullable PyExpression location,
                                                            AccessDirection direction,
                                                            PyResolveContext resolveContext) {
        return Collections.emptyList();
    }

    @Override
    public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public void assertValid(@Nullable String message) {
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeVariable.getName());
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof PyInferenceVariable otherVariable)) {
            return false;
        }
        if (this.getClass() != other.getClass()) {
            return false;
        }
        if (!name.equals(otherVariable.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return name;
    }
}
