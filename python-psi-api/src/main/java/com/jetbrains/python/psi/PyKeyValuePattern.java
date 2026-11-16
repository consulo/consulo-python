// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstKeyValuePattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyKeyValuePattern extends PyPattern {
  default PyPattern getKeyPattern() {
    return findNotNullChildByClass(this, PyPattern.class);
  }

  default @Nullable PyPattern getValuePattern() {
    return ObjectUtil.tryCast(getLastChild(), PyPattern.class);
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }
}
