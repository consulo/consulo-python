// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyType;
import consulo.util.lang.lazy.LazyValue;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared base for set-of-members {@link PyCompositeType}s ({@link PyUnionType}, {@link PyUnsafeUnionType}, {@link PyIntersectionType}).
 * Provides the single memoized {@code hashCode} + fast-path {@code equals}, avoiding the structural-equality
 * storm on deeply nested composite types.
 */
public abstract class PyCompositeTypeBase implements PyCompositeType {
    /**
     * Members compared for equality/hashing; must be effectively immutable (the hash code is memoized from it).
     */
    protected abstract Set<@Nullable PyType> getMemberSet();

    private final Supplier<Integer> cachedHashCode = LazyValue.atomicNotNull(() -> getMemberSet().hashCode());

    @Override
    public final int hashCode() {
        return cachedHashCode.get();
    }

    @Override
    public final boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false; // different kinds are never equal
        }
        PyCompositeTypeBase otherBase = (PyCompositeTypeBase)other;
        return cachedHashCode.get().equals(otherBase.cachedHashCode.get()) && getMemberSet().equals(otherBase.getMemberSet());
    }
}
