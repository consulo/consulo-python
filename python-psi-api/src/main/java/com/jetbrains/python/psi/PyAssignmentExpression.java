// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import org.jspecify.annotations.Nullable;

/**
 * Represents assignment expressions introduced in Python 3.8 (PEP 572).
 */
// TODO consulo: JB extends com.jetbrains.python.ast.PyAstAssignmentExpression (python-ast layer not ported),
//  its default methods are placed here
public interface PyAssignmentExpression extends PyExpression {
  /**
   * @return LHS of an expression (before :=), null if underlying target is not an identifier.
   */
  default @Nullable PyTargetExpression getTarget() {
    return getFirstChild() instanceof PyTargetExpression target ? target : null;
  }

  /**
   * @return RHS of an expression (after :=), null if assigned value is omitted or not an expression.
   */
  default @Nullable PyExpression getAssignedValue() {
    return getLastChild() instanceof PyExpression expression ? expression : null;
  }
}
