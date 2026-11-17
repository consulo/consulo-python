// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import org.jspecify.annotations.Nullable;

public record PyTypedDictFieldStub(String name, @Nullable String type, boolean isReadOnly) {
}
