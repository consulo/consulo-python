// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import java.util.Objects;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstValuePattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyValuePattern extends PyPattern {
  @Override
  default boolean isIrrefutable() {
    return false;
  }

  default PyReferenceExpression getValue() {
    return Objects.requireNonNull(findChildByClass(this, PyReferenceExpression.class));
  }
}
