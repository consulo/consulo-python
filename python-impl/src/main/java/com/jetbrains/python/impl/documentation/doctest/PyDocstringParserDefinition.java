/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import jakarta.annotation.Nonnull;

/**
 * @author ktisha
 */
@ExtensionImpl
public class PyDocstringParserDefinition extends PythonParserDefinition {
    public static final IFileElementType PYTHON_DOCSTRING_FILE = new PyDocstringFileElementType(PyDocstringLanguageDialect.INSTANCE);

    @Nonnull
    @Override
    public Language getLanguage() {
        return PyDocstringLanguageDialect.INSTANCE;
    }

    @Nonnull
    @Override
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new PyDocstringLexer();
    }

    @Nonnull
    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new PyDocstringParser();
    }


    @Nonnull
    @Override
    public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
        return TokenSet.orSet(super.getWhitespaceTokens(languageVersion), TokenSet.create(PyDocstringTokenTypes.DOTS));
    }

    @Nonnull
    @Override
    public IFileElementType getFileNodeType() {
        return PYTHON_DOCSTRING_FILE;
    }

    @Nonnull
    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyDocstringFile(viewProvider);
    }
}
