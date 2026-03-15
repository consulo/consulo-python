package com.jetbrains.python.debugger;

import org.jspecify.annotations.Nullable;

import consulo.execution.debug.XSourcePosition;


public interface PyPositionConverter {

  PySourcePosition create(String file, int line);

  PySourcePosition convertToPython(XSourcePosition position);

  @Nullable
  XSourcePosition convertFromPython(PySourcePosition position);

  PySignature convertSignature(PySignature signature);
}
