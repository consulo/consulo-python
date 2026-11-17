// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

/**
 * Java counterpart of the top-level functions declared in JB {@code PyDebugUtils.kt}.
 */
public final class PyDebugUtilsKt {
    private PyDebugUtilsKt() {
    }

    public static String getQuotingString(QuotingPolicy policy, String value) {
        return switch (policy) {
            case SINGLE -> value;
            case DOUBLE -> value.replace("'", "\"");
            case NONE -> value.replace("'", "");
        };
    }
}
