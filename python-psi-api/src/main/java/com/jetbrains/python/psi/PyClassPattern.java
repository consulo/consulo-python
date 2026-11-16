// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstClassPattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyClassPattern extends PyPattern {
  Set<String> SPECIAL_BUILTINS = Set.of(
    "bool", "bytearray", "bytes", "dict", "float", "frozenset", "int", "list", "set", "str", "tuple");

  default PyReferenceExpression getClassNameReference() {
    return Objects.requireNonNull(findChildByClass(this, PyReferenceExpression.class));
  }

  default PyPatternArgumentList getArgumentList() {
    return Objects.requireNonNull(findChildByClass(this, PyPatternArgumentList.class));
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }
}
