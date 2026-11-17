// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@ExtensionImpl(id = "dataclassFieldStub", order = "after typingAliasStub")
public class PyDataclassFieldStubType extends CustomTargetExpressionStubType<PyDataclassFieldStub> {

    @Override
    public @Nullable PyDataclassFieldStub createStub(PyTargetExpression psi) {
        return PyDataclassFieldStubImpl.create(psi);
    }

    @Override
    public @Nullable PyDataclassFieldStub deserializeStub(StubInputStream stream) throws IOException {
        return PyDataclassFieldStubImpl.deserialize(stream);
    }
}
