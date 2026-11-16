// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public interface PyCaptureContext extends PyElement {
  @Nullable
  PyType getCaptureTypeForChild(PyPattern pattern, TypeEvalContext context);

  /**
   * Determines what type this pattern would have if it was a capture pattern (like a bare name or _).
   * <p>
   * In pattern matching, a capture pattern takes on the type of the entire matched expression,
   * regardless of any specific pattern constraints.
   * <p>
   * For example:
   * <pre>{@code
   * x: int | str
   * match x:
   *     case a:         # This is a capture pattern
   *         # Here 'a' has type int | str
   *     case str():     # This is a class pattern
   *         # Capture type: int | str (same as what 'case a:' would get)
   *         # Regular getType: str
   *
   * y: int
   * match y:
   *     case str() as a:
   *         # Capture type: int (same as what 'case a:' would get)
   *         # Regular getType: intersect(int, str) (just 'str' for now)
   * }</pre>
   *
   * @see PyPattern#getType(TypeEvalContext, TypeEvalContext.Key)
   */
  static @Nullable PyType getCaptureType(PyPattern pattern, TypeEvalContext context) {
    // TODO consulo: JB falls back to PyAnyType.unknown ('?: PyAnyType.unknown'); PyAnyType is not ported,
    //  in Consulo the unknown type is represented by null
    return PyUtil.getNullableParameterizedCachedValue(pattern, context, ctx -> {
      PyCaptureContext captureContext = PsiTreeUtil.getParentOfType(pattern, PyCaptureContext.class);
      return captureContext != null ? captureContext.getCaptureTypeForChild(pattern, context) : null;
    });
  }
}
