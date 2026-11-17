// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstContinueStatement;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;


public interface PyContinueStatement extends PyAstContinueStatement, PyStatement {
    @Override
    @RequiredReadAction
    default @Nullable PyLoopStatement getLoopStatement() {
        return (PyLoopStatement)PyAstContinueStatement.super.getLoopStatement();
    }
}
