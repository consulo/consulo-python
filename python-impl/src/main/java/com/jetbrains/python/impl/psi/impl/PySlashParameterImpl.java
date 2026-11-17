// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;

public class PySlashParameterImpl extends PyBaseElementImpl<PySlashParameterStub> implements PySlashParameter {

    public PySlashParameterImpl(ASTNode node) {
        super(node);
    }

    public PySlashParameterImpl(PySlashParameterStub stub) {
        super(stub, PyStubElementTypes.SLASH_PARAMETER);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPySlashParameter(this);
    }
}
