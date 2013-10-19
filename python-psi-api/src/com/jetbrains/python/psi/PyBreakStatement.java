package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyBreakStatement extends PyStatement {
  @Nullable
  PyLoopStatement getLoopStatement();
}
