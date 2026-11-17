// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstExpressionStatement;
import consulo.annotation.access.RequiredReadAction;


public interface PyExpressionStatement extends PyAstExpressionStatement, PyStatement {
    @Override
    @RequiredReadAction
    default PyExpression getExpression() {
        return (PyExpression)PyAstExpressionStatement.super.getExpression();
    }
}
