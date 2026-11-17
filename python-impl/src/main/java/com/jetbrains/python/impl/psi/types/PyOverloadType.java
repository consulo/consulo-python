// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.lazy.LazyValue;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class PyOverloadType implements PyType {
    private final List<PyCallableType> myItems;
    private final @Nullable Ref<@Nullable PyType> myImpl;

    public PyOverloadType(List<PyCallableType> items, @Nullable Ref<@Nullable PyType> impl) {
        myItems = items;
        myImpl = impl;
        cachedHashCode = LazyValue.atomicNotNull(() -> 31 * myItems.hashCode() + Objects.hashCode(myImpl != null ? myImpl.get() : null));
    }

    public List<PyCallableType> getItems() {
        return myItems;
    }

    public @Nullable Ref<@Nullable PyType> getImpl() {
        return myImpl;
    }

    @Override
    public List<RatedResolveResult> resolveMember(String name,
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
    public String getName() {
        return PyNames.OVERLOAD_TYPE;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(@Nullable String message) {
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyOverloadType(this);
        }
        return visitor.visitPyType(this);
    }

    private final Supplier<Integer> cachedHashCode;

    @Override
    public int hashCode() {
        return cachedHashCode.get();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PyOverloadType otherType)) {
            return false;
        }
        return cachedHashCode.get().equals(otherType.cachedHashCode.get()) &&
            myItems.equals(otherType.myItems) &&
            Objects.equals(myImpl != null ? myImpl.get() : null, otherType.myImpl != null ? otherType.myImpl.get() : null);
    }
}
