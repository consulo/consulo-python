// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstSequencePattern (python-ast layer not ported),
//  its default methods are placed here
public interface PySequencePattern extends PyPattern {
  default List<PyPattern> getElements() {
    return Arrays.asList(findChildrenByClass(this, PyPattern.class));
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }
}
