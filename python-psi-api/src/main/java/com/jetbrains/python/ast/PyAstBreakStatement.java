// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.ast.impl.PyUtilCore;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

public interface PyAstBreakStatement extends PyAstStatement {
    @RequiredReadAction
    default @Nullable PyAstLoopStatement getLoopStatement() {
        return PyUtilCore.getCorrespondingLoop(this);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyBreakStatement(this);
    }
}
