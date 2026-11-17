// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.ast.IElementType;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyAnnotationImpl;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;

import java.io.IOException;

public class PyAnnotationElementType extends PyStubElementType<PyAnnotationStub, PyAnnotation> {
    public PyAnnotationElementType() {
        this("ANNOTATION");
    }

    public PyAnnotationElementType(String debugName) {
        super(debugName);
    }

    @Override
    public PyAnnotation createPsi(PyAnnotationStub stub) {
        return new PyAnnotationImpl(stub);
    }

    @Override
    public PyAnnotationStub createStub(PyAnnotation psi, StubElement parentStub) {
        return new PyAnnotationStubImpl(parentStub, PyStubElementTypes.ANNOTATION);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PyAnnotationImpl(node);
    }

    @Override
    public void serialize(PyAnnotationStub stub, StubOutputStream dataStream)
        throws IOException {
    }

    @Override
    public PyAnnotationStub deserialize(StubInputStream dataStream, StubElement parentStub)
        throws IOException {
        return new PyAnnotationStubImpl(parentStub, PyStubElementTypes.ANNOTATION);
    }

    @Override
    public boolean shouldCreateStub(ASTNode node) {
        IElementType parentType = node.getTreeParent().getElementType();
        return PythonDialectsTokenSetProvider.getInstance().getFunctionDeclarationTokens().contains(parentType)
               || PythonDialectsTokenSetProvider.getInstance().getParameterTokens().contains(parentType);
    }
}