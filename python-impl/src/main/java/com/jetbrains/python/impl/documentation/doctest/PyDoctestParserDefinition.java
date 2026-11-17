// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.PythonParserDefinition;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;

/**
 * User : ktisha
 */
@ExtensionImpl
public final class PyDoctestParserDefinition extends PythonParserDefinition {
    public static final IFileElementType PYTHON_DOCTEST_FILE = new PyDocstringFileElementType(PyDoctestLanguageDialect
        .getInstance());

    @Override
    public Language getLanguage() {
        return PyDoctestLanguageDialect.getInstance();
    }

    @Override
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new PyDoctestLexer();
    }

    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new PyDoctestParser();
    }


    @Override
    public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
        return TokenSet.orSet(super.getWhitespaceTokens(languageVersion), TokenSet.create(PyDoctestTokenTypes.DOTS));
    }

    @Override
    public IFileElementType getFileNodeType() {
        return PYTHON_DOCTEST_FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyDoctestFile(viewProvider);
    }
}
