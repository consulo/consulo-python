// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.psi.impl.PyTypeParameterListImpl;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import java.io.IOException;

public class PyTypeParameterListElementType extends PyStubElementType<PyTypeParameterListStub, PyTypeParameterList> {

    public PyTypeParameterListElementType() {
        super("TYPE_PARAMETER_LIST");
    }

    @Override
    public PyTypeParameterList createPsi(PyTypeParameterListStub stub) {
        return new PyTypeParameterListImpl(stub);
    }

    @Override
    public PyTypeParameterListStub createStub(PyTypeParameterList psi, StubElement parentStub) {
        return new PyTypeParameterListStubImpl(parentStub, getStubElementType());
    }

    @Override
    public void serialize(PyTypeParameterListStub stub, StubOutputStream dataStream) throws IOException {

    }

    @Override
    public PyTypeParameterListStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        return new PyTypeParameterListStubImpl(parentStub, getStubElementType());
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyTypeParameterListImpl(node);
    }

    protected IStubElementType<PyTypeParameterListStub, PyTypeParameterList> getStubElementType() {
        return PyStubElementTypes.TYPE_PARAMETER_LIST;
    }
}
