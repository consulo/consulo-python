// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

/**
 * Declared in JB {@code PyDataFlow.kt}.
 */
public enum Reachability {
    REACHABLE,
    UNREACHABLE,
    UNREACHABLE_FOR_TYPE_CHECKING
}
