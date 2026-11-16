// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstKeywordPattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyKeywordPattern extends PyPattern {
  default String getKeyword() {
    return getKeywordElement().getText();
  }

  default PsiElement getKeywordElement() {
    return getFirstChild();
  }

  default @Nullable PyPattern getValuePattern() {
    return findChildByClass(this, PyPattern.class);
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }

  default @Nullable PyClassPattern getContainingClassPattern() {
    return getParent().getParent() instanceof PyClassPattern classPattern ? classPattern : null;
  }
}
