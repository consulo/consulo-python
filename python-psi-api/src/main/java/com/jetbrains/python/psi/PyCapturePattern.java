// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstCapturePattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyCapturePattern extends PyPattern {
  @Override
  default boolean isIrrefutable() {
    return true;
  }

  default PyTargetExpression getTarget() {
    PyTargetExpression target = findChildByClass(this, PyTargetExpression.class);
    if (target == null) {
      throw new IllegalArgumentException(this + ": target cannot be null");
    }
    return target;
  }
}
