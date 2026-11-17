// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeHints;

import com.jetbrains.python.PythonFileType;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

class PyTypeHintFileType extends PythonFileType {
    public static final PyTypeHintFileType INSTANCE = new PyTypeHintFileType();

    private PyTypeHintFileType() {
        super(PyTypeHintDialect.INSTANCE);
    }

    @Override
    public String getId() {
        return "PythonTypeHint";
    }

    @Override
    public LocalizeValue getDescription() {
        return PyLocalize.filetypePythonTypeHintDescription();
    }

    @Override
    public String getDefaultExtension() {
        return "typeHint";
    }
}
