package com.jetbrains.python.debugger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.execution.debug.XSourcePosition;


public interface PyPositionConverter {

  @Nonnull
  PySourcePosition create(@Nonnull String file, int line);

  @Nonnull
  PySourcePosition convertToPython(@Nonnull XSourcePosition position);

  @Nullable
  XSourcePosition convertFromPython(@Nonnull PySourcePosition position);

  PySignature convertSignature(PySignature signature);
}
