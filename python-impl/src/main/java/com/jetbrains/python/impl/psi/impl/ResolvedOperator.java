// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference.OperatorSide;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ResolvedOperator(PyType selfType,
                               @Nullable PyType method,
                               List<PyExpression> arguments,
                               OperatorSide side) {
}
