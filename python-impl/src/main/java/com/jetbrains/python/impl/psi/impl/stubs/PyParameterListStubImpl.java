// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.stubs.PyParameterListStub;

public class PyParameterListStubImpl extends StubBase<PyParameterList> implements PyParameterListStub {
    public PyParameterListStubImpl(StubElement parent, IStubElementType stubElementType) {
        super(parent, stubElementType);
    }
}