// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.PythonFileType;

public abstract class PyStubElementType<StubT extends StubElement<?>, PsiT extends PyElement> extends IStubElementType<StubT, PsiT> {
    public PyStubElementType(String debugName) {
        super(debugName, PythonFileType.INSTANCE.getLanguage());
    }

    @Override
    public String toString() {
        return "Py:" + super.toString();
    }

    public abstract PsiElement createElement(ASTNode node);

    @Override
    public void indexStub(StubT stub, IndexSink sink) {
    }

    @Override
    public String getExternalId() {
        return "py." + super.toString();
    }
}
