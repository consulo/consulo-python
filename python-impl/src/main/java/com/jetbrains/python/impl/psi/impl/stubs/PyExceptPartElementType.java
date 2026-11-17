// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyExceptPartImpl;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

import java.io.IOException;

public class PyExceptPartElementType extends PyStubElementType<PyExceptPartStub, PyExceptPart> {
    public PyExceptPartElementType() {
        super("EXCEPT_PART");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyExceptPartImpl(node);
    }

    @Override
    public PyExceptPart createPsi(PyExceptPartStub stub) {
        return new PyExceptPartImpl(stub);
    }

    @Override
    public PyExceptPartStub createStub(PyExceptPart psi, StubElement parentStub) {
        return new PyExceptPartStubImpl(parentStub);
    }

    @Override
    public void serialize(PyExceptPartStub stub, StubOutputStream dataStream) throws IOException {
    }

    @Override
    public PyExceptPartStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        return new PyExceptPartStubImpl(parentStub);
    }
}
