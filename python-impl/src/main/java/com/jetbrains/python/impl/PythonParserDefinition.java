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

package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.lexer.PythonIndentingLexer;
import com.jetbrains.python.impl.parsing.PyParser;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
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

import javax.annotation.Nonnull;

/**
 * @author yole
 * @author Keith Lea
 */
@ExtensionImpl
public class PythonParserDefinition implements ParserDefinition {
  private final TokenSet myWhitespaceTokens;
  private final TokenSet myCommentTokens;
  private final TokenSet myStringLiteralTokens;

  public PythonParserDefinition() {
    myWhitespaceTokens = TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
    myCommentTokens = TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
    myStringLiteralTokens = TokenSet.orSet(PyTokenTypes.STRING_NODES, TokenSet.create(PyElementTypes.STRING_LITERAL_EXPRESSION));
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }

  @Nonnull
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new PythonIndentingLexer();
  }

  public IFileElementType getFileNodeType() {
    return PyFileElementType.INSTANCE;
  }

  @Nonnull
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return myWhitespaceTokens;
  }

  @Nonnull
  public TokenSet getCommentTokens(LanguageVersion languageVersion) {
    return myCommentTokens;
  }

  @Nonnull
  public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
    return myStringLiteralTokens;
  }

  @Nonnull
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new PyParser();
  }

  @Nonnull
  public PsiElement createElement(@Nonnull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof PyElementType) {
      PyElementType pyElType = (PyElementType)type;
      return pyElType.createElement(node);
    }
    else if (type instanceof PyStubElementType) {
      return ((PyStubElementType)type).createElement(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    // see LanguageTokenSeparatorGenerator instead
    return SpaceRequirements.MAY;
  }

}
