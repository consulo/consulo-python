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

package com.jetbrains.python.buildout.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.buildout.config.lexer.BuildoutCfgFlexLexer;
import com.jetbrains.python.buildout.config.psi.BuildoutCfgASTFactory;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import consulo.lang.LanguageVersion;

/**
 * @author traff
 */
public class BuildoutCfgParserDefinition implements ParserDefinition, BuildoutCfgElementTypes, BuildoutCfgTokenTypes {
  private final BuildoutCfgASTFactory astFactory = new BuildoutCfgASTFactory();

  @Nonnull
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new BuildoutCfgFlexLexer();
  }

  @Nullable
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new BuildoutCfgParser();
  }

  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @Nonnull
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return TokenSet.create(WHITESPACE);
  }

  @Nonnull
  public TokenSet getCommentTokens(LanguageVersion languageVersion) {
    return TokenSet.create(COMMENT);
  }

  @Nonnull
  public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
    return TokenSet.create(TEXT);
  }

  @Nonnull
  public PsiElement createElement(final ASTNode node) {
    return astFactory.create(node);
  }

  public PsiFile createFile(final FileViewProvider viewProvider) {
    return new BuildoutCfgFile(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
