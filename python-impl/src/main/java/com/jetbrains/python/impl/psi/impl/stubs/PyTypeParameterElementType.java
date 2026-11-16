// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.psi.impl.PyTypeParameterImpl;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import java.io.IOException;

public class PyTypeParameterElementType extends PyStubElementType<PyTypeParameterStub, PyTypeParameter> {

    public PyTypeParameterElementType() {
        super("TYPE_PARAMETER");
    }

    @Override
    public PyTypeParameter createPsi(PyTypeParameterStub stub) {
        return new PyTypeParameterImpl(stub);
    }

    @Override
    public PyTypeParameterStub createStub(PyTypeParameter psi, StubElement parentStub) {
        return new PyTypeParameterStubImpl(psi.getName(), psi.getKind(),
            psi.getBoundExpression() != null ? psi.getBoundExpression().getText() : null,
            psi.getDefaultExpression() != null ? psi.getDefaultExpression().getText() : null,
            parentStub, getStubElementType());
    }

    @Override
    public void serialize(PyTypeParameterStub stub, StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeVarInt(stub.getKind().getIndex());
        dataStream.writeName(stub.getBoundExpressionText());
        dataStream.writeName(stub.getDefaultExpressionText());
    }

    @Override
    public PyTypeParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        PyTypeParameter.Kind kind = PyTypeParameter.Kind.fromIndex(dataStream.readVarInt());
        String boundExpressionText = dataStream.readNameString();
        String defaultExpressionText = dataStream.readNameString();
        return new PyTypeParameterStubImpl(name,
            kind,
            boundExpressionText,
            defaultExpressionText,
            parentStub, getStubElementType());
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyTypeParameterImpl(node);
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.TYPE_PARAMETER;
    }
}
