// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStubElementType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;

@ExtensionImpl
public class PythonParserDefinition implements ParserDefinition {

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @Override
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new PythonIndentingLexer();
    }

    @Override
    public IFileElementType getFileNodeType() {
        return PyFileElementType.INSTANCE;
    }

    @Override
    public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
        return TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
    }

    @Override
    public TokenSet getCommentTokens(LanguageVersion languageVersion) {
        return TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
    }

    @Override
    public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
        return TokenSet.orSet(PyTokenTypes.STRING_NODES, PyTokenTypes.FSTRING_TOKENS);
    }

    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new PyParser();
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();
        if (type instanceof PyElementType pyElType) {
            return pyElType.createElement(node);
        }
        else if (type instanceof PyStubElementType) {
            return ((PyStubElementType<?, ?>)type).createElement(node);
        }
        return new ASTWrapperPsiElement(node);
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PyFileImpl(viewProvider);
    }

    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        // see LanguageTokenSeparatorGenerator instead
        return SpaceRequirements.MAY;
    }
}
