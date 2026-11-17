// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstDoubleStarExpression;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

public interface PyDoubleStarExpression extends PyAstDoubleStarExpression, PyExpression {
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getExpression() {
        return (PyExpression)PyAstDoubleStarExpression.super.getExpression();
    }
}
