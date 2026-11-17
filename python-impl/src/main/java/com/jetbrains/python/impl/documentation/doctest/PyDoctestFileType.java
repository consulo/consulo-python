// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

/**
 * User : ktisha
 */
public class PyDoctestFileType extends PythonFileType {
    public static final PythonFileType INSTANCE = new PyDoctestFileType();

    private PyDoctestFileType() {
        super(new PyDoctestLanguageDialect());
    }

    @Override
    public String getId() {
        return PyNames.PY_DOCSTRING_ID;
    }

    @Override
    public LocalizeValue getDescription() {
        return PyLocalize.filetypePythonDocstringDescription();
    }

    @Override
    public String getDefaultExtension() {
        return "doctest";
    }
}
