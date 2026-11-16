// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;

public class PyTypeParameterListStubImpl extends StubBase<PyTypeParameterList> implements PyTypeParameterListStub {
    public PyTypeParameterListStubImpl(StubElement parent, IStubElementType stubElementType) {
        super(parent, stubElementType);
    }
}
