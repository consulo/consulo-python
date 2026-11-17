// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import org.jspecify.annotations.Nullable;

public interface PyEnumAttributeStub extends CustomTargetExpressionStub {
  @Nullable PyLiteralKind getLiteralKind();

  boolean isMember();
}
