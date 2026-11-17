// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyDecoratorListImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;

import java.io.IOException;

public class PyDecoratorListElementType extends PyStubElementType<PyDecoratorListStub, PyDecoratorList> {

    public PyDecoratorListElementType() {
        super("DECORATOR_LIST");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyDecoratorListImpl(node);
    }

    @Override
    public PyDecoratorList createPsi(PyDecoratorListStub stub) {
        return new PyDecoratorListImpl(stub);
    }

    @Override
    public PyDecoratorListStub createStub(PyDecoratorList psi, StubElement parentStub) {
        return new PyDecoratorListStubImpl(parentStub);
    }

    @Override
    public void serialize(PyDecoratorListStub stub, StubOutputStream dataStream) throws IOException {
        // nothing
    }

    @Override
    public PyDecoratorListStub deserialize(StubInputStream dataStream, StubElement parentStub)
        throws IOException {
        return new PyDecoratorListStubImpl(parentStub);
    }
}
