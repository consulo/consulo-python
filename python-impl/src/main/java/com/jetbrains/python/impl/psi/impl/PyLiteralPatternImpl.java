// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyLiteralPatternImpl extends PyElementImpl implements PyLiteralPattern {
  public PyLiteralPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLiteralPattern(this);
  }

  @Override
  public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    // TODO consulo: PyLiteralType is not ported, JB first tries:
    //  PyType literalType = PyLiteralType.Companion.fromLiteralParameter(getExpression(), context, false);
    //  if (literalType != null) {
    //    return literalType;
    //  }
    return context.getType(getExpression());
  }
}
