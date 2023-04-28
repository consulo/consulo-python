package com.jetbrains.python.debugger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.execution.debug.XSourcePosition;


public interface PyPositionConverter {

  @Nonnull
  PySourcePosition create(@Nonnull final String file, final int line);

  @Nonnull
  PySourcePosition convertToPython(@Nonnull final XSourcePosition position);

  @Nullable
  XSourcePosition convertFromPython(@Nonnull final PySourcePosition position);

  PySignature convertSignature(PySignature signature);
}
