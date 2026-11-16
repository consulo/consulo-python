// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import java.util.Objects;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstSingleStarPattern (python-ast layer not ported),
//  its default methods are placed here
public interface PySingleStarPattern extends PyPattern {
  default PyPattern getPattern() {
    return Objects.requireNonNull(findChildByClass(this, PyPattern.class));
  }

  @Override
  default boolean isIrrefutable() {
    return true;
  }
}
