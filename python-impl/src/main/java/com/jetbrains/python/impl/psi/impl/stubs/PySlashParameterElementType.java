// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.psi.impl.PySlashParameterImpl;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import java.io.IOException;

public class PySlashParameterElementType extends PyStubElementType<PySlashParameterStub, PySlashParameter> {

    public PySlashParameterElementType() {
        super("SLASH_PARAMETER");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PySlashParameterImpl(node);
    }

    @Override
    public PySlashParameter createPsi(PySlashParameterStub stub) {
        return new PySlashParameterImpl(stub);
    }

    @Override
    public PySlashParameterStub createStub(PySlashParameter psi, StubElement parentStub) {
        return new PySlashParameterStubImpl(parentStub);
    }

    @Override
    public void serialize(PySlashParameterStub stub, StubOutputStream dataStream) throws IOException {
    }

    @Override
    public PySlashParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) {
        return new PySlashParameterStubImpl(parentStub);
    }
}
