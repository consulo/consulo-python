// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyTypeAliasStatementImpl;
import com.jetbrains.python.impl.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.impl.psi.stubs.PyTypeAliasNameIndex;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.lang.Version;

import java.io.IOException;

public class PyTypeAliasStatementElementType extends PyStubElementType<PyTypeAliasStatementStub, PyTypeAliasStatement> {

    public PyTypeAliasStatementElementType() {
        super("TYPE_ALIAS_STATEMENT");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyTypeAliasStatementImpl(node);
    }

    @Override
    public PyTypeAliasStatement createPsi(PyTypeAliasStatementStub stub) {
        return new PyTypeAliasStatementImpl(stub);
    }

    @Override
    public PyTypeAliasStatementStub createStub(PyTypeAliasStatement psi, StubElement parentStub) {
        return new PyTypeAliasStatementStubImpl(psi.getName(), (psi.getTypeExpression() != null ? psi.getTypeExpression().getText() : null),
            parentStub, getStubElementType(), PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi));
    }

    @Override
    public void indexStub(PyTypeAliasStatementStub stub, IndexSink sink) {
        String name = stub.getName();
        if (name != null && PyUtil.getInitialUnderscores(name) == 0 && stub.getParentStub() instanceof PyFileStub) {
            sink.occurrence(PyTypeAliasNameIndex.KEY, name);
            sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
        }
    }

    @Override
    public void serialize(PyTypeAliasStatementStub stub, StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeName(stub.getTypeExpressionText());
        PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
    }

    @Override
    public PyTypeAliasStatementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String typeExpressionText = dataStream.readNameString();
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);

        return new PyTypeAliasStatementStubImpl(name, typeExpressionText, parentStub, getStubElementType(), versions);
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.TYPE_ALIAS_STATEMENT;
    }
}
