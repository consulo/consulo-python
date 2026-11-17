// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements.parser;

import com.intellij.python.requirements.RequirementsFile;
import com.intellij.python.requirements.RequirementsLanguage;
import com.intellij.python.requirements.parser.psi.RequirementsTypes;
import com.intellij.python.requirements.parser.psi.impl.RequirementsTypesFactory;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;

@ExtensionImpl
public class RequirementsParserDefinition implements ParserDefinition {
    private static final TokenSet STRING_LITERALS = TokenSet.create(RequirementsTypes.QUOTED_STRING_TOKEN);
    private static final TokenSet COMMENTS = TokenSet.create(RequirementsTypes.COMMENT);
    private static final IFileElementType FILE = new IFileElementType(RequirementsLanguage.INSTANCE);

    @Override
    public Language getLanguage() {
        return RequirementsLanguage.INSTANCE;
    }

    @Override
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new RequirementsLexerAdapter();
    }

    @Override
    public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
        return STRING_LITERALS;
    }

    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new RequirementsParser();
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public TokenSet getCommentTokens(LanguageVersion languageVersion) {
        return COMMENTS;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new RequirementsFile(viewProvider);
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return RequirementsTypesFactory.createElement(node);
    }
}
