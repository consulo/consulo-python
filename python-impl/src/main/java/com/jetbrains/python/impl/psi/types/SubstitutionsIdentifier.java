// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import org.jspecify.annotations.Nullable;

/**
 * JB: top-level {@code data class SubstitutionsIdentifier} of {@code TypeEvalContextImpl.kt}.
 */
public record SubstitutionsIdentifier(PyExpression expression, @Nullable PyCallableType callableType) {
    public SubstitutionsIdentifier(PyExpression expression) {
        this(expression, null);
    }
}
