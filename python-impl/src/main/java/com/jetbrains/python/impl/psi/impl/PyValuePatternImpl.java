// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyValuePattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyValuePatternImpl extends PyElementImpl implements PyValuePattern {
  public PyValuePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyValuePattern(this);
  }

  @Override
  public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    return context.getType(getValue());
  }

  // TODO consulo: PyLiteralType is not ported, JB overrides:
  //  public boolean canExcludePatternType(TypeEvalContext context) {
  //    return (context.getType(getValue()) instanceof PyLiteralType);
  //  }
}
