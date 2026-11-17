// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.serialization;

import consulo.util.xml.serializer.Accessor;
import consulo.util.xml.serializer.SerializationFilter;
import consulo.util.xml.serializer.SerializationFilterBase;
import org.jspecify.annotations.Nullable;

/**
 * Returns true only if all filters return true
 * @author Ilya.Kazakevich
 */
public final class CompoundFilter extends SerializationFilterBase {
    private final SerializationFilter[] myFilters;

    public CompoundFilter(final SerializationFilter... filters) {
        myFilters = filters.clone();
    }

    @Override
    protected boolean accepts(final Accessor accessor, final Object bean, final @Nullable Object beanValue) {
        for (final SerializationFilter filter : myFilters) {
            if (!filter.accepts(accessor, bean)) {
                return false;
            }
        }
        return true;
    }
}
