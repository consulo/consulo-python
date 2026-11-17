// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

import java.io.IOException;

public class PyNamedParameterElementType extends PyStubElementType<PyNamedParameterStub, PyNamedParameter> {
    private static final int POSITIONAL_CONTAINER = 1;
    private static final int KEYWORD_CONTAINER = 2;

    public PyNamedParameterElementType() {
        this("NAMED_PARAMETER");
    }

    public PyNamedParameterElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PyNamedParameter createPsi(PyNamedParameterStub stub) {
        return new PyNamedParameterImpl(stub);
    }

    @Override
    public PyNamedParameterStub createStub(PyNamedParameter psi, StubElement parentStub) {
        return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), psi.getDefaultValueText(),
                                            psi.getTypeCommentAnnotation(), psi.getAnnotationValue(), parentStub, getStubElementType());
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyNamedParameterImpl(node);
    }

    @Override
    public void serialize(PyNamedParameterStub stub, StubOutputStream dataStream)
        throws IOException {
        dataStream.writeName(stub.getName());

        byte flags = 0;
        if (stub.isPositionalContainer()) {
            flags |= POSITIONAL_CONTAINER;
        }
        if (stub.isKeywordContainer()) {
            flags |= KEYWORD_CONTAINER;
        }
        dataStream.writeByte(flags);
        dataStream.writeName(stub.getDefaultValueText());
        dataStream.writeName(stub.getTypeComment());
        dataStream.writeName(stub.getAnnotation());
    }

    @Override
    public PyNamedParameterStub deserialize(StubInputStream dataStream, StubElement parentStub)
        throws IOException {
        String name = dataStream.readNameString();
        byte flags = dataStream.readByte();
        String defaultValueText = dataStream.readNameString();
        String typeComment = dataStream.readNameString();
        String annotation = dataStream.readNameString();
        return new PyNamedParameterStubImpl(name,
                                            (flags & POSITIONAL_CONTAINER) != 0,
                                            (flags & KEYWORD_CONTAINER) != 0,
                                            defaultValueText,
                                            typeComment,
                                            annotation,
                                            parentStub, getStubElementType());
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        ASTNode paramList = node.getTreeParent();
        if (paramList != null) {
            ASTNode container = paramList.getTreeParent();
            if (container != null && container.getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
                return false;
            }
        }
        return super.shouldCreateStub(node);
    }

    protected IStubElementType getStubElementType() {
        return PyStubElementTypes.NAMED_PARAMETER;
    }
}