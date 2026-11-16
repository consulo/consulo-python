package com.jetbrains.python.psi;

import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstAsPattern (python-ast layer not ported),
//  its default methods are placed here
public interface PyAsPattern extends PyPattern {
  default PyPattern getPattern() {
    PyPattern pattern = findChildByClass(this, PyPattern.class);
    if (pattern == null) {
      throw new IllegalArgumentException(this + ": pattern cannot be null");
    }
    return pattern;
  }

  default @Nullable PyTargetExpression getTarget() {
    return findChildByClass(this, PyTargetExpression.class);
  }

  @Override
  default boolean isIrrefutable() {
    return getPattern().isIrrefutable();
  }
}
