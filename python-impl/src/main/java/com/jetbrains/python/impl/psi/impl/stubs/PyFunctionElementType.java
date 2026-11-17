// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import consulo.language.ast.ASTNode;
import consulo.util.lang.Version;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionStub;

import java.io.IOException;

public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction> {
    public PyFunctionElementType() {
        this("FUNCTION_DECLARATION");
    }

    public PyFunctionElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyFunctionImpl(node);
    }

    @Override
    public PyFunction createPsi(PyFunctionStub stub) {
        return new PyFunctionImpl(stub);
    }

    @Override
    public PyFunctionStub createStub(PyFunction psi, StubElement parentStub) {
        PyFunctionImpl function = (PyFunctionImpl)psi;
        String message = function.extractDeprecationMessage();
        PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
        String typeComment = function.getTypeCommentAnnotation();
        String annotationContent = function.getAnnotationValue();
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi);
        return new PyFunctionStubImpl(psi.getName(),
                                      PyPsiUtils.strValue(docStringExpression),
                                      message,
                                      function.isAsync(),
                                      function.isGenerator(),
                                      function.onlyRaisesNotImplementedError(),
                                      typeComment,
                                      annotationContent,
                                      parentStub,
                                      getStubElementType(),
                                      versions);
    }

    @Override
    public void serialize(PyFunctionStub stub, StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());
        dataStream.writeUTFFast(StringUtil.notNullize(stub.getDocString()));
        dataStream.writeName(stub.getDeprecationMessage());
        dataStream.writeBoolean(stub.isAsync());
        dataStream.writeBoolean(stub.isGenerator());
        dataStream.writeBoolean(stub.onlyRaisesNotImplementedError());
        dataStream.writeName(stub.getTypeComment());
        dataStream.writeName(stub.getAnnotation());
        PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
    }

    @Override
    public PyFunctionStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        String name = dataStream.readNameString();
        String docString = dataStream.readUTFFast();
        String deprecationMessage = dataStream.readNameString();
        boolean isAsync = dataStream.readBoolean();
        boolean isGenerator = dataStream.readBoolean();
        boolean onlyRaisesNotImplementedError = dataStream.readBoolean();
        String typeComment = dataStream.readNameString();
        String annotationContent = dataStream.readNameString();
        RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);
        return new PyFunctionStubImpl(name,
                                      StringUtil.nullize(docString),
                                      deprecationMessage,
                                      isAsync,
                                      isGenerator,
                                      onlyRaisesNotImplementedError,
                                      typeComment,
                                      annotationContent,
                                      parentStub,
                                      getStubElementType(),
                                      versions);
    }

    @Override
    public void indexStub(PyFunctionStub stub, IndexSink sink) {
        String name = stub.getName();
        if (name != null) {
            sink.occurrence(PyFunctionNameIndex.KEY, name);
            if (stub.getParentStub() instanceof PyFileStub && PyUtil.getInitialUnderscores(name) == 0) {
                sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
            }
        }
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.FUNCTION_DECLARATION;
    }
}