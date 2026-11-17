// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.impl.PythonParserDefinition;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.psi.PyTypeRepresentationFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;

@ExtensionImpl
public class PyTypeRepresentationParserDefinition extends PythonParserDefinition {
    @Override
    public Language getLanguage() {
        return PyTypeRepresentationDialect.INSTANCE;
    }

    @Override
    public TokenSet getCommentTokens(LanguageVersion languageVersion) {
        return TokenSet.EMPTY;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyTypeRepresentationFile(viewProvider);
    }

    @Override
    public IFileElementType getFileNodeType() {
        return PyTypeRepresentationFileElementType.INSTANCE;
    }

    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new PyTypeRepresentationParser();
    }
}
