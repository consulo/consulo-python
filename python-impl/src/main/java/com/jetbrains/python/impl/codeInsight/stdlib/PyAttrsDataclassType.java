// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;

/**
 * Declared in PyAttrsDataclassParametersProvider.kt
 */
public final class PyAttrsDataclassType implements PyDataclassParameters.Type {
    public static final PyAttrsDataclassType INSTANCE = new PyAttrsDataclassType();

    private PyAttrsDataclassType() {
    }

    @Override
    public String getName() {
        return "ATTRS";
    }

    @Override
    public boolean isDataclassTransformBased() {
        return false;
    }

    @Override
    public PyDataclassResolver getResolver() {
        return PyAttrsDataclassResolver.INSTANCE;
    }
}
