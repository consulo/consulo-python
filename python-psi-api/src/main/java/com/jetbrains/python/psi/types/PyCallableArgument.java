// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;
import org.jspecify.annotations.Nullable;

/**
 * A call argument represented by its expression, or a positional argument represented only by its type.
 */
public class PyCallableArgument {
    private final @Nullable PyExpression myExpression;
    private final @Nullable PyType myType;

    public PyCallableArgument(PyExpression expression) {
        myExpression = expression;
        myType = null;
    }

    public PyCallableArgument(@Nullable PyType type) {
        myExpression = null;
        myType = type;
    }

    public @Nullable PyExpression getExpression() {
        return myExpression;
    }

    public @Nullable PyType getType(TypeEvalContext context) {
        return myExpression != null ? context.getType(myExpression) : myType;
    }
}
