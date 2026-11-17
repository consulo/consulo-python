// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PyTypedDictStub extends CustomTargetExpressionStub {

  /**
   * @return TypedDict's name.
   */
  String getName();

  /**
   * @return keys' names and their values' types.
   * Iteration order repeats the declaration order.
   */
  List<PyTypedDictFieldStub> getFields();

  /**
   * @return value of 'total' keyword argument if exists, True otherwise
   */
  boolean isRequired();

  /**
   * @return value of 'closed' keyword argument if exists, False otherwise
   */
  boolean isClosed();

  /**
   * @return value of 'extra_items' keyword argument if exists, null otherwise
   */
  @Nullable String getExtraItemsType();
}
