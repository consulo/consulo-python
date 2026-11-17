// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.serialization;

import consulo.util.xml.serializer.Accessor;
import consulo.util.xml.serializer.SerializationFilterBase;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Property;
import consulo.util.xml.serializer.annotation.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Only accepts accessors with xmlb tags
 * @author Ilya.Kazakevich
 */
public final class AnnotationSerializationFilter extends SerializationFilterBase {
    @Override
    protected boolean accepts(final Accessor accessor, final Object bean, final @Nullable Object beanValue) {
        return accessor.getAnnotation(Property.class) != null
            || accessor.getAnnotation(Tag.class) != null
            || accessor.getAnnotation(Attribute.class) != null;
    }
}
