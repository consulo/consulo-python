// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.impl.PyImportElementImpl;
import com.jetbrains.python.psi.stubs.PyImportElementStub;

import java.io.IOException;

public class PyImportElementElementType extends PyStubElementType<PyImportElementStub, PyImportElement> {
    public PyImportElementElementType() {
        this("IMPORT_ELEMENT");
    }

    public PyImportElementElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyImportElementImpl(node);
    }

    @Override
    public PyImportElement createPsi(PyImportElementStub stub) {
        return new PyImportElementImpl(stub);
    }

    @Override
    public PyImportElementStub createStub(PyImportElement psi, StubElement parentStub) {
        PyTargetExpression asName = psi.getAsNameElement();
        return new PyImportElementStubImpl(psi.getImportedQName(), asName != null ? asName.getName() : "", parentStub, getStubElementType());
    }

    @Override
    public void serialize(PyImportElementStub stub, StubOutputStream dataStream) throws IOException {
        QualifiedName.serialize(stub.getImportedQName(), dataStream);
        dataStream.writeName(stub.getAsName());
    }

    @Override
    public PyImportElementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        QualifiedName qName = QualifiedName.deserialize(dataStream);
        String asName = dataStream.readNameString();
        return new PyImportElementStubImpl(qName, asName, parentStub, getStubElementType());
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.IMPORT_ELEMENT;
    }
}
