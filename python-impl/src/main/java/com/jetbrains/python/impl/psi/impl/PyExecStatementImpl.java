// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyExecStatement;

public class PyExecStatementImpl extends PyElementImpl implements PyExecStatement {
    public PyExecStatementImpl(ASTNode astNode) {
        super(astNode);
    }
}
