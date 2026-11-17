// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstStarArgument;
import org.jspecify.annotations.Nullable;

public interface PyStarArgument extends PyAstStarArgument, PyExpression {

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getExpression() {
        return (PyExpression)PyAstStarArgument.super.getExpression();
    }
}
