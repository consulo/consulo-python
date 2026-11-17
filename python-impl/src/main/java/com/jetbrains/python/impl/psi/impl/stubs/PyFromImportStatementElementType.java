// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import consulo.language.ast.ASTNode;
import consulo.util.lang.Version;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyFromImportStatementImpl;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;

import java.io.IOException;


public class PyFromImportStatementElementType extends PyStubElementType<PyFromImportStatementStub, PyFromImportStatement> {
    public PyFromImportStatementElementType() {
        this("FROM_IMPORT_STATEMENT");
    }

    public PyFromImportStatementElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyFromImportStatementImpl(node);
    }

    @Override
    public PyFromImportStatement createPsi(PyFromImportStatementStub stub) {
        return new PyFromImportStatementImpl(stub);
    }

    @Override
    public PyFromImportStatementStub createStub(PyFromImportStatement psi, StubElement parentStub) {
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi);
        return new PyFromImportStatementStubImpl(psi.getImportSourceQName(), psi.isStarImport(), psi.getRelativeLevel(), parentStub,
                                                 getStubElementType(), versions);
    }

    @Override
    public void serialize(PyFromImportStatementStub stub, StubOutputStream dataStream) throws IOException {
        QualifiedName qName = stub.getImportSourceQName();
        QualifiedName.serialize(qName, dataStream);
        dataStream.writeBoolean(stub.isStarImport());
        dataStream.writeVarInt(stub.getRelativeLevel());
        PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
    }

    @Override
    public PyFromImportStatementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        QualifiedName qName = QualifiedName.deserialize(dataStream);
        boolean isStarImport = dataStream.readBoolean();
        int relativeLevel = dataStream.readVarInt();
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);
        return new PyFromImportStatementStubImpl(qName, isStarImport, relativeLevel, parentStub, getStubElementType(), versions);
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.FROM_IMPORT_STATEMENT;
    }
}
