// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;


public class PyTryExceptStatementImpl extends PyElementImpl implements PyTryExceptStatement {
    private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_PART);

    public PyTryExceptStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTryExceptStatement(this);
    }

    @Override
    public PyExceptPart[] getExceptParts() {
        return childrenToPsi(EXCEPT_BLOCKS, PyExceptPart.EMPTY_ARRAY);
    }
}
