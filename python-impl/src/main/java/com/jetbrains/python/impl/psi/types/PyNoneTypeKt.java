// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jspecify.annotations.Nullable;

public final class PyNoneTypeKt {
    private PyNoneTypeKt() {
    }

    public static boolean isNoneType(@Nullable PyType type) {
        return type instanceof PyClassType classType && classType.getClassQName() != null &&
            PyNames.TYPE_NONE_NAMES.contains(classType.getClassQName());
    }
}
