// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;


public class PySingleStarParameterImpl extends PyBaseElementImpl<PySingleStarParameterStub> implements PySingleStarParameter {
    public PySingleStarParameterImpl(ASTNode astNode) {
        super(astNode);
    }

    public PySingleStarParameterImpl(PySingleStarParameterStub stub) {
        super(stub, PyStubElementTypes.SINGLE_STAR_PARAMETER);
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPySingleStarParameter(this);
    }
}
