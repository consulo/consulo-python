// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.impl.PythonParserDefinition;
import com.jetbrains.python.pyi.PyiLanguageDialect;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;

@ExtensionImpl
public final class PyiParserDefinition extends PythonParserDefinition {
    public static final IFileElementType PYTHON_STUB_FILE = new PyiFileElementType(PyiLanguageDialect.getInstance());

    @Override
    public Language getLanguage() {
        return PyiLanguageDialect.getInstance();
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyiFile(viewProvider);
    }

    @Override
    public IFileElementType getFileNodeType() {
        return PYTHON_STUB_FILE;
    }
}
