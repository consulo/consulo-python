// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeHints;

import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.language.file.FileViewProvider;
import consulo.virtualFileSystem.fileType.FileType;

public class PyTypeHintFile extends PyFileImpl implements PyExpressionCodeFragment {

    PyTypeHintFile(FileViewProvider viewProvider) {
        super(viewProvider, PyTypeHintDialect.INSTANCE);
    }

    @Override
    public FileType getFileType() {
        return PyTypeHintFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "TypeHint:" + getName();
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        // The same as for .pyi files
        return LanguageLevel.getLatest();
    }
}
