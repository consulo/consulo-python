// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import org.jspecify.annotations.Nullable;

/**
 * Declared in JB {@code PyDataclasses.kt}.
 *
 * @param parameterAlias The alias to use as the generated {@code __init__} parameter name, or {@code null} to fall back to the field name.
 *                       Precedence is resolved by the owning framework's resolver;
 */
public record PyDataclassFieldParameters(boolean hasDefault,
                                         boolean hasDefaultFactory,
                                         boolean initValue,
                                         boolean kwOnly,
                                         @Nullable String parameterAlias) {
    public boolean getHasDefault() {
        return hasDefault;
    }

    public boolean getHasDefaultFactory() {
        return hasDefaultFactory;
    }

    public boolean getInitValue() {
        return initValue;
    }

    public boolean getKwOnly() {
        return kwOnly;
    }

    public @Nullable String getParameterAlias() {
        return parameterAlias;
    }
}
