// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.PyElementTypes;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;
import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByType;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstMatchStatement (python-ast layer not ported),
//  its default methods are placed here
public interface PyMatchStatement extends PyCompoundStatement {
  default @Nullable PyExpression getSubject() {
    return findChildByClass(this, PyExpression.class);
  }

  default List<PyCaseClause> getCaseClauses() {
    return findChildrenByType(this, PyElementTypes.CASE_CLAUSE);
  }
}
