// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record PyCspSolution(boolean failed,
                            boolean complete,
                            Map<SubstitutionsIdentifier, Map<PyTypeParameterType, Ref<@Nullable PyType>>> instantiations) {
}
