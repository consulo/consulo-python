// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstStatementPart;

/**
 * Abstract part of a multipart statement.
 */
public interface PyStatementPart extends PyAstStatementPart, PyStatementListContainer {
    PyStatementPart[] EMPTY_ARRAY = new PyStatementPart[0];

    @Override
    @RequiredReadAction
    default PyStatementList getStatementList() {
        return (PyStatementList)PyAstStatementPart.super.getStatementList();
    }
}
