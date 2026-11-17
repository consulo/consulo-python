// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.PythonFileType;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

public final class PyTypeRepresentationFileType extends PythonFileType {
    public static final PyTypeRepresentationFileType INSTANCE = new PyTypeRepresentationFileType();

    private PyTypeRepresentationFileType() {
        super(PyTypeRepresentationDialect.INSTANCE);
    }

    @Override
    public String getId() {
        return "PythonTypeRepresentation";
    }

    @Override
    public LocalizeValue getDescription() {
        return PyLocalize.filetypePythonTypeRepresentationDescription();
    }

    @Override
    public String getDefaultExtension() {
        return "pythonTypeRepresentation";
    }
}
