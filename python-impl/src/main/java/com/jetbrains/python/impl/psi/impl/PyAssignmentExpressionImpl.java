// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyAssignmentExpressionImpl extends PyElementImpl implements PyAssignmentExpression {

  public PyAssignmentExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    final PyTargetExpression target = getTarget();
    // TODO consulo: JB returns PyAnyType.getUnknown() instead of null (PyAnyType not ported)
    return target == null ? null : context.getType(target);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentExpression(this);
  }
}
