// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;

/**
 * Declared in PyStdlibDataclassParametersProvider.kt
 */
public final class PyStdlibDataclassType implements PyDataclassParameters.Type {
    public static final PyStdlibDataclassType INSTANCE = new PyStdlibDataclassType();

    private PyStdlibDataclassType() {
    }

    @Override
    public String getName() {
        return "STD";
    }

    @Override
    public boolean isDataclassTransformBased() {
        return false;
    }

    @Override
    public PyDataclassResolver getResolver() {
        return PyStdlibDataclassResolver.INSTANCE;
    }
}
