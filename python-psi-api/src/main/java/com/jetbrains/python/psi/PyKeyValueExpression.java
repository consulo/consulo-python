// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstKeyValueExpression;
import org.jspecify.annotations.Nullable;

public interface PyKeyValueExpression extends PyAstKeyValueExpression, PyExpression {
    PyKeyValueExpression[] EMPTY_ARRAY = new PyKeyValueExpression[0];

    @Override
    @RequiredReadAction
    default PyExpression getKey() {
        return (PyExpression)PyAstKeyValueExpression.super.getKey();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getValue() {
        return (PyExpression)PyAstKeyValueExpression.super.getValue();
    }
}
