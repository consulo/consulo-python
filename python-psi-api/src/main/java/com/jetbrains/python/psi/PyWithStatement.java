// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstWithStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;

public interface PyWithStatement extends PyAstWithStatement, PyCompoundStatement, PyNamedElementContainer, PyStatementListContainer {
    @Override
    @RequiredReadAction
    default PyStatementList getStatementList() {
        return (PyStatementList)PyAstWithStatement.super.getStatementList();
    }

    @Override
    PyWithItem[] getWithItems();

    boolean isSuppressingExceptions(TypeEvalContext context);
}
