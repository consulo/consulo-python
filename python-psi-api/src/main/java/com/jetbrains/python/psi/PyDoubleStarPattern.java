// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstDoubleStarPattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyDoubleStarPattern extends PyPattern {
  @Override
  default boolean isIrrefutable() {
    return true;
  }
}
