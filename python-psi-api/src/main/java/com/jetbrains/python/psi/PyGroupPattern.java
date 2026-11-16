// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstGroupPattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyGroupPattern extends PyPattern {
  default PyPattern getPattern() {
    return findNotNullChildByClass(this, PyPattern.class);
  }

  @Override
  default boolean isIrrefutable() {
    return getPattern().isIrrefutable();
  }
}
