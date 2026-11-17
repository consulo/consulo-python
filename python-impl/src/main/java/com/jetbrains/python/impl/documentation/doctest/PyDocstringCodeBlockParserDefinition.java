// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.PythonParserDefinition;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;

/**
 * This parses a plain Python code block from a docstring into a {@link PyDocstringCodeBlockFile}.
 * The block has no doctest prompt, so this class reuses the plain Python lexer and parser from
 * {@link PythonParserDefinition}.
 */
@ExtensionImpl
public final class PyDocstringCodeBlockParserDefinition extends PythonParserDefinition {
    public static final IFileElementType PYTHON_DOCSTRING_CODE_BLOCK_FILE =
        new PyDocstringCodeBlockFileElementType(PyDocstringCodeBlockLanguageDialect.getInstance());

    @Override
    public Language getLanguage() {
        return PyDocstringCodeBlockLanguageDialect.getInstance();
    }

    @Override
    public IFileElementType getFileNodeType() {
        return PYTHON_DOCSTRING_CODE_BLOCK_FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyDocstringCodeBlockFile(viewProvider);
    }
}
