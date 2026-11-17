// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

public class PyDocstringCodeBlockFileType extends PythonFileType {
    public static final PythonFileType INSTANCE = new PyDocstringCodeBlockFileType();

    private PyDocstringCodeBlockFileType() {
        super(new PyDocstringCodeBlockLanguageDialect());
    }

    @Override
    public String getId() {
        return PyNames.PY_DOCSTRING_CODE_BLOCK_ID;
    }

    @Override
    public LocalizeValue getDescription() {
        return PyLocalize.filetypePythonDocstringCodeblockDescription();
    }

    @Override
    public String getDefaultExtension() {
        return "docstringcodeblock";
    }
}
