// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStub;
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStubType;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public final class PyFunctoolsWrapsDecoratorStub implements PyCustomDecoratorStub {
  private final String wrapped;

  public PyFunctoolsWrapsDecoratorStub(String wrapped) {
    this.wrapped = wrapped;
  }

  public String getWrapped() {
    return wrapped;
  }

  @Override
  public Class<? extends PyCustomDecoratorStubType<?>> getTypeClass() {
    return PyFunctoolsWrapsDecoratorStubType.class;
  }

  @Override
  public void serialize(StubOutputStream stream) throws IOException {
    stream.writeName(wrapped);
  }

  @Override
  public String toString() {
    return "PyFunctoolsWrapsDecoratorStub(wrapped='" + wrapped + "')";
  }

  public static @Nullable PyFunctoolsWrapsDecoratorStub create(PyDecorator psi) {
    QualifiedName qName = psi.getQualifiedName();
    if (qName == null) return null;
    if (!PyKnownDecoratorUtil.asKnownDecorators(qName).contains(PyKnownDecorator.FUNCTOOLS_WRAPS)) return null;
    PyArgumentList argumentList = psi.getArgumentList();
    if (argumentList == null) return null;
    if (!(argumentList.getValueExpressionForParam(PyKnownDecoratorUtil.FunctoolsWrapsParameters.WRAPPED) instanceof PyReferenceExpression wrappedExpr)) {
      return null;
    }
    QualifiedName wrappedExprQName = wrappedExpr.asQualifiedName();
    if (wrappedExprQName == null) return null;
    return new PyFunctoolsWrapsDecoratorStub(wrappedExprQName.toString());
  }
}
