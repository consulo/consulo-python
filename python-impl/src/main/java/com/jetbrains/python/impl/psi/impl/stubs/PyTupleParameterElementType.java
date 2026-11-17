// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.impl.psi.impl.PyTupleParameterImpl;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;

import java.io.IOException;

/**
 * Does actual storing and loading of tuple parameter stub. Not much to do.
 */
public class PyTupleParameterElementType extends PyStubElementType<PyTupleParameterStub, PyTupleParameter> {

    public PyTupleParameterElementType() {
        super("TUPLE_PARAMETER");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyTupleParameterImpl(node);
    }

    @Override
    public PyTupleParameter createPsi(PyTupleParameterStub stub) {
        return new PyTupleParameterImpl(stub);
    }

    @Override
    public PyTupleParameterStub createStub(PyTupleParameter psi, StubElement parentStub) {
        return new PyTupleParameterStubImpl(psi.getDefaultValueText(), parentStub);
    }

    @Override
    public PyTupleParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        return new PyTupleParameterStubImpl(dataStream.readNameString(), parentStub);
    }

    @Override
    public void serialize(PyTupleParameterStub stub, StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getDefaultValueText());
    }
}
