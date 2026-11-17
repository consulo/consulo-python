// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import java.util.Set;

/**
 * Top-level declarations of JB {@code PyDataclassVisitor.kt}.
 */
public final class PyDataclassVisitorKt {
    static final Set<String> ORDER_OPERATORS = Set.of("__lt__", "__le__", "__gt__", "__ge__");

    private PyDataclassVisitorKt() {
    }
}
