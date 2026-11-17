// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.stubs.PyNamedTupleStub;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@ExtensionImpl(id = "namedTupleStub", order = "after propertyStub")
public final class PyNamedTupleStubType extends CustomTargetExpressionStubType<PyNamedTupleStub> {

    @Override
    public @Nullable PyNamedTupleStub createStub(PyTargetExpression psi) {
        return PyNamedTupleStubImpl.create(psi);
    }

    @Override
    public @Nullable PyNamedTupleStub deserializeStub(StubInputStream stream) throws IOException {
        return PyNamedTupleStubImpl.deserialize(stream);
    }
}
