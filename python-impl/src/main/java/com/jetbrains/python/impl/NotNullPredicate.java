// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.google.common.base.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Filters out nullable elements allowing children to filter not-null elements
 *
 * @author Ilya.Kazakevich
 * @deprecated use java 8 primitives instead
 */
@Deprecated(forRemoval = true)
public class NotNullPredicate<T> implements Predicate<T> {
    /**
     * Simply filters nulls
     */
    public static final Predicate<Object> INSTANCE = new NotNullPredicate<>();

    @Override
    public final boolean apply(@Nullable T input) {
        if (input == null) {
            return false;
        }
        return applyNotNull(input);
    }

    protected boolean applyNotNull(T input) {
        return true;
    }
}
