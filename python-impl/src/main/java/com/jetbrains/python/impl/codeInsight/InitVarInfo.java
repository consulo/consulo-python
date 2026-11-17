// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jspecify.annotations.Nullable;

/**
 * A resolved {@code InitVar[...]} pseudo-field: the declaring attribute and the unwrapped init-only type.
 * <p>
 * Declared in JB {@code PyDataclasses.kt}.
 */
public record InitVarInfo(PyTargetExpression targetExpression, @Nullable PyType type) {
    public PyTargetExpression getTargetExpression() {
        return targetExpression;
    }

    public @Nullable PyType getType() {
        return type;
    }
}
