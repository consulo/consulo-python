// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PySingleStarParameterImpl;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;

import java.io.IOException;

public class PySingleStarParameterElementType extends PyStubElementType<PySingleStarParameterStub, PySingleStarParameter> {
    public PySingleStarParameterElementType() {
        super("SINGLE_STAR_PARAMETER");
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return new PySingleStarParameterImpl(node);
    }

    @Override
    public PySingleStarParameter createPsi(PySingleStarParameterStub stub) {
        return new PySingleStarParameterImpl(stub);
    }

    @Override
    public PySingleStarParameterStub createStub(PySingleStarParameter psi, StubElement parentStub) {
        return new PySingleStarParameterStubImpl(parentStub);
    }

    @Override
    public void serialize(PySingleStarParameterStub stub, StubOutputStream dataStream) throws IOException {
    }

    @Override
    public PySingleStarParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
        return new PySingleStarParameterStubImpl(parentStub);
    }
}
