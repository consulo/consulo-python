// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.mlcompletion;

public enum PyCompletionMlElementKind {
    NAMED_ARG,
    KEYWORD,
    PACKAGE_OR_MODULE,
    FROM_TARGET,
    TYPE_OR_CLASS,
    FUNCTION,
    UNKNOWN;

    public PyCompletionMlElementInfo asInfo() {
        return new PyCompletionMlElementInfo(this, false);
    }
}
