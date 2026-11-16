// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;

public class PySlashParameterStubImpl extends StubBase<PySlashParameter> implements PySlashParameterStub {

    public PySlashParameterStubImpl(StubElement parent) {
        super(parent, PyStubElementTypes.SLASH_PARAMETER);
    }
}
