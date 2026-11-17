// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeHints;

import com.jetbrains.python.impl.PythonParserDefinition;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;

@ExtensionImpl
public final class PyTypeHintParserDefinition extends PythonParserDefinition {
    @Override
    public Language getLanguage() {
        return PyTypeHintDialect.INSTANCE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyTypeHintFile(viewProvider);
    }

    @Override
    public IFileElementType getFileNodeType() {
        return PyTypeHintFileElementType.INSTANCE;
    }

    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new PyTypeHintParser();
    }
}
