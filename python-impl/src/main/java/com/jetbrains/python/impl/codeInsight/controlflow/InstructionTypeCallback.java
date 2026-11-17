// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;


public interface InstructionTypeCallback {
    @Nullable
    Ref<PyType> getType(TypeEvalContext context);
}
