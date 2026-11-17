// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import org.jspecify.annotations.Nullable;

public interface PyXDebugValue {
    String getName();

    @Nullable
    String getType();

    PyFrameAccessor getFrameAccessor();

    void setFrameAccessor(PyFrameAccessor frameAccessor);

    PyDebugValue toPyDebugValue();
}
