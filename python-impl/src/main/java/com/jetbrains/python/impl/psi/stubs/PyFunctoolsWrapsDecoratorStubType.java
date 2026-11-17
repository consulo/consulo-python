// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@ExtensionImpl
public final class PyFunctoolsWrapsDecoratorStubType implements PyCustomDecoratorStubType<PyFunctoolsWrapsDecoratorStub> {
  @Override
  public @Nullable PyFunctoolsWrapsDecoratorStub createStub(PyDecorator psi) {
    return PyFunctoolsWrapsDecoratorStub.create(psi);
  }

  @Override
  public @Nullable PyFunctoolsWrapsDecoratorStub deserializeStub(StubInputStream stream) throws IOException {
    String name = stream.readNameString();
    if (name == null) return null;
    return new PyFunctoolsWrapsDecoratorStub(name);
  }
}
