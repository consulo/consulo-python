// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;

public class PyDecoratorListImpl extends PyBaseElementImpl<PyDecoratorListStub> implements PyDecoratorList {

    public PyDecoratorListImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyDecoratorList(this);
    }

    public PyDecoratorListImpl(PyDecoratorListStub stub) {
        super(stub, PyStubElementTypes.DECORATOR_LIST);
    }

    @Override
    public PyDecorator[] getDecorators() {
        PyDecorator[] decoarray = new PyDecorator[0];
        return getStubOrPsiChildren(PyStubElementTypes.DECORATOR_CALL, decoarray);
        //return decoarray;
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        PsiElement childPsi = child.getPsi();
        if (childPsi instanceof PyDecorator && getDecorators().length == 1) {
            delete();
            return;
        }
        super.deleteChildInternal(child);
    }
}
