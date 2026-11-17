// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyParameterListImpl;
import com.jetbrains.python.psi.stubs.PyParameterListStub;

import java.io.IOException;

public class PyParameterListElementType extends PyStubElementType<PyParameterListStub, PyParameterList> {
    public PyParameterListElementType() {
        this("PARAMETER_LIST");
    }

    public PyParameterListElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PyParameterList createPsi(PyParameterListStub stub) {
        return new PyParameterListImpl(stub);
    }

    @Override
    public PyParameterListStub createStub(PyParameterList psi, StubElement parentStub) {
        return new PyParameterListStubImpl(parentStub, getStubElementType());
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyParameterListImpl(node);
    }

    @Override
    public void serialize(PyParameterListStub stub, StubOutputStream dataStream)
        throws IOException {
    }

    @Override
    public PyParameterListStub deserialize(StubInputStream dataStream, StubElement parentStub)
        throws IOException {
        return new PyParameterListStubImpl(parentStub, getStubElementType());
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.PARAMETER_LIST;
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        if (node.getTreeParent().getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
            return false;
        }
        return super.shouldCreateStub(node);
    }
}