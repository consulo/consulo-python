// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jspecify.annotations.Nullable;

public class PyImportElementStubImpl extends StubBase<PyImportElement> implements PyImportElementStub {
    private final @Nullable QualifiedName myImportedQName;
    private final String myAsName;

    public PyImportElementStubImpl(@Nullable QualifiedName importedQName, String asName, StubElement parent,
                                   IStubElementType elementType) {
        super(parent, elementType);
        myImportedQName = importedQName;
        myAsName = asName;
    }

    @Override
    public @Nullable QualifiedName getImportedQName() {
        return myImportedQName;
    }

    @Override
    public String getAsName() {
        return myAsName;
    }

    @Override
    public String toString() {
        return "PyImportElementStubImpl{" +
               "myImportedQName=" + myImportedQName +
               ", myAsName='" + myAsName + '\'' +
               '}';
    }
}
