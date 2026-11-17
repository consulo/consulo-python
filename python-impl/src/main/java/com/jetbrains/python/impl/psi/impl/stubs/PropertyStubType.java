// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.stubs.PropertyStubStorage;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@ExtensionImpl(id = "propertyStub")
public final class PropertyStubType extends CustomTargetExpressionStubType<PropertyStubStorage> {
    @Override
    public @Nullable PropertyStubStorage createStub(PyTargetExpression psi) {
        return PropertyStubStorage.fromCall(psi.findAssignedValue());
    }

    @Override
    public PropertyStubStorage deserializeStub(StubInputStream stream) throws IOException {
        return PropertyStubStorage.deserialize(stream);
    }
}
