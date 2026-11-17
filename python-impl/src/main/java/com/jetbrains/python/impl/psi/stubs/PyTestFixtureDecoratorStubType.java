// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.impl.testing.pyTestFixtures.PyTestFixtureUtilsKt;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@ExtensionImpl
public final class PyTestFixtureDecoratorStubType implements PyCustomDecoratorStubType<PyTestFixtureDecoratorStub> {
  @Override
  public @Nullable PyTestFixtureDecoratorStub createStub(PyDecorator psi) {
    QualifiedName qName = psi.getQualifiedName();
    if (qName == null || !PyTestFixtureUtilsKt.TEST_FIXTURE_DECORATOR_NAMES.contains(qName.toString())) {
      return null;
    }
    String testFixtureName = PyTestFixtureUtilsKt.getTestFixtureName(psi);
    if (testFixtureName == null) return null;
    return new PyTestFixtureDecoratorStubImpl(testFixtureName);
  }

  @Override
  public @Nullable PyTestFixtureDecoratorStub deserializeStub(StubInputStream stream) throws IOException {
    String name = stream.readNameString();
    if (name == null) return null;
    return new PyTestFixtureDecoratorStubImpl(name);
  }

  private static final class PyTestFixtureDecoratorStubImpl implements PyTestFixtureDecoratorStub {
    private final String name;

    private PyTestFixtureDecoratorStubImpl(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Class<PyTestFixtureDecoratorStubType> getTypeClass() {
      return PyTestFixtureDecoratorStubType.class;
    }

    @Override
    public void serialize(StubOutputStream stream) throws IOException {
      stream.writeName(name);
    }

    @Override
    public String toString() {
      return "PyTestFixtureDecoratorStub(name='" + name + "')";
    }
  }
}
