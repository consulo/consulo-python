// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;

/**
 * Declared in PyDataclassTransformParametersProvider.kt
 */
public final class PyDataclassTransformType implements PyDataclassParameters.Type {
    public static final PyDataclassTransformType INSTANCE = new PyDataclassTransformType();

    private PyDataclassTransformType() {
    }

    @Override
    public String getName() {
        return "DATACLASS_TRANSFORM";
    }

    @Override
    public boolean isDataclassTransformBased() {
        return true;
    }

    @Override
    public PyDataclassResolver getResolver() {
        return PyDataclassTransformResolver.INSTANCE;
    }
}
