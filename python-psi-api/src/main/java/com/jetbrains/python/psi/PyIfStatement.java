// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.ast.PyAstIfStatement;

/**
 * The 'if/elif/else' statement.
 */
public interface PyIfStatement extends PyAstIfStatement, PyCompoundStatement, PyStatementWithElse {
    @Override
    @RequiredReadAction
    default PyIfPart getIfPart() {
        return (PyIfPart)PyAstIfStatement.super.getIfPart();
    }

    @Override
    @RequiredReadAction
    default PyIfPart[] getElifParts() {
        return childrenToPsi(PyElementTypes.ELIFS, PyIfPart.EMPTY_ARRAY);
    }
}
