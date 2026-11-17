// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class PyNeverType implements PyType {
    public static final PyNeverType NEVER = new PyNeverType("Never");
    public static final PyNeverType NO_RETURN = new PyNeverType("NoReturn");

    public static @Nullable PyType toNoReturnIfNeeded(@Nullable PyType type) {
        return type == NEVER ? NO_RETURN : type;
    }

    public static @Nullable PyType convertNoReturnToNever(@Nullable PyType type) {
        return type == NO_RETURN ? NEVER : type;
    }

    private final String myName;

    private PyNeverType(String name) {
        myName = name;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public void assertValid(@Nullable String message) {
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PyNeverType;
    }

    @Override
    public int hashCode() {
        return myName.hashCode();
    }

    @Override
    public List<RatedResolveResult> resolveMember(
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext resolveContext
    ) {
        return Collections.emptyList();
    }

    @Override
    public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyNeverType(this);
    }
}
