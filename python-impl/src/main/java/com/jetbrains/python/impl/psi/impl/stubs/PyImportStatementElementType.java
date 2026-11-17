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
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyImportStatementImpl;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;

import java.io.IOException;


public class PyImportStatementElementType extends PyStubElementType<PyImportStatementStub, PyImportStatement> {
    public PyImportStatementElementType() {
        this("IMPORT_STATEMENT");
    }

    public PyImportStatementElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyImportStatementImpl(node);
    }

    @Override
    public PyImportStatement createPsi(PyImportStatementStub stub) {
        return new PyImportStatementImpl(stub);
    }

    @Override
    public PyImportStatementStub createStub(PyImportStatement psi, StubElement parentStub) {
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi);
        return new PyImportStatementStubImpl(parentStub, getStubElementType(), versions);
    }

    @Override
    public void serialize(PyImportStatementStub stub, StubOutputStream dataStream) throws IOException {
        PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
    }

    @Override
    public PyImportStatementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);
        return new PyImportStatementStubImpl(parentStub, getStubElementType(), versions);
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.IMPORT_STATEMENT;
    }
}