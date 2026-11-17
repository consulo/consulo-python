// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeHints;

import com.jetbrains.python.impl.psi.PyFileElementType;

class PyTypeHintFileElementType extends PyFileElementType {
    public static final PyTypeHintFileElementType INSTANCE = new PyTypeHintFileElementType();

    private PyTypeHintFileElementType() {
        super(PyTypeHintDialect.INSTANCE);
    }

    @Override
    public String getExternalId() {
        return "PyTypeHint.ID";
    }
}
