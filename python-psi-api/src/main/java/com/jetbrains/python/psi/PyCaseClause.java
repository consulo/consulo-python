// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstCaseClause (python-ast layer not ported),
//  its default methods are placed here
public interface PyCaseClause extends PyStatementPart {
  default @Nullable PyPattern getPattern() {
    return findChildByClass(this, PyPattern.class);
  }

  default @Nullable PyExpression getGuardCondition() {
    return findChildByClass(this, PyExpression.class);
  }

  @Nullable
  PyType getSubjectTypeAfter(TypeEvalContext context);
}
