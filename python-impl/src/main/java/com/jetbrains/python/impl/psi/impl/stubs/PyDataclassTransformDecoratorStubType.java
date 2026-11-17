// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.psi.stubs.PyDataclassTransformDecoratorStub;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@ExtensionImpl
public class PyDataclassTransformDecoratorStubType implements PyCustomDecoratorStubType<PyDataclassTransformDecoratorStub> {
    @Override
    public @Nullable PyDataclassTransformDecoratorStub createStub(PyDecorator decorator) {
        return PyDataclassTransformDecoratorStub.create(decorator);
    }

    @Override
    public PyDataclassTransformDecoratorStub deserializeStub(StubInputStream stream) throws IOException {
        return PyDataclassTransformDecoratorStub.deserialize(stream);
    }
}
