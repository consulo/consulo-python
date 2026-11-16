// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstPatternArgumentList (python-ast layer not ported),
//  its default methods are placed here
public interface PyPatternArgumentList extends PyElement {
  default List<PyPattern> getPatterns() {
    return Arrays.asList(findChildrenByClass(this, PyPattern.class));
  }
}
