// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFormattedStringElement;
import consulo.language.ast.ASTNode;

public class PyFormattedStringElementImpl extends PyElementImpl implements PyFormattedStringElement {

    public PyFormattedStringElementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyFormattedStringElement(this);
    }
}
