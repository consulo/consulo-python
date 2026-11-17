// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyStarImportElementImpl;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;

import java.io.IOException;

public class PyStarImportElementElementType extends PyStubElementType<PyStarImportElementStub, PyStarImportElement> {
    public PyStarImportElementElementType() {
        super("STAR_IMPORT_ELEMENT");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyStarImportElementImpl(node);
    }

    @Override
    public PyStarImportElement createPsi(PyStarImportElementStub stub) {
        return new PyStarImportElementImpl(stub);
    }

    @Override
    public PyStarImportElementStub createStub(PyStarImportElement psi, StubElement parentStub) {
        return new PyStarImportElementStubImpl(parentStub);
    }

    @Override
    public void serialize(PyStarImportElementStub stub, StubOutputStream dataStream) throws IOException {
    }

    @Override
    public PyStarImportElementStub deserialize(StubInputStream dataStream, StubElement parentStub)
        throws IOException {
        return new PyStarImportElementStubImpl(parentStub);
    }
}
