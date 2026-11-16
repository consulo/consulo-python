// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyAsPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyAsPatternImpl extends PyElementImpl implements PyAsPattern {
  public PyAsPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAsPattern(this);
  }

  @Override
  public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    return context.getType(getPattern());
  }

  @Override
  public boolean canExcludePatternType(TypeEvalContext context) {
    return getPattern().canExcludePatternType(context);
  }
}
