// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.fstrings;

/**
 * The kind of one recognized piece of a format spec.
 */
public enum PyFormatSpecComponentKind {
    /**
     * The padding character of a {@code [fill]align} pair. It carries no meaning of its own.
     */
    FILL,
    ALIGN,
    SIGN,
    COERCE_NEGATIVE_ZERO,
    ALTERNATE_FORM,
    ZERO_PADDING,
    /**
     * A run of digits that gives the minimum field width.
     */
    WIDTH,
    GROUPING,
    /**
     * The {@code .} that starts the precision.
     */
    PRECISION_MARK,
    /**
     * The run of digits after {@link #PRECISION_MARK}.
     */
    PRECISION,
    /**
     * The presentation type that ends the spec.
     */
    TYPE,
    /**
     * A {@code %} directive of a datetime spec, such as {@code %Y}.
     */
    DATETIME_DIRECTIVE,
}
