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

package com.jetbrains.python.codeInsight.regexp;

import java.util.EnumSet;

import javax.annotation.Nonnull;

import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParser;
import org.intellij.lang.regexp.RegExpParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import consulo.lang.LanguageVersion;

/**
 * @author yole
 */
public class PythonRegexpParserDefinition extends RegExpParserDefinition {
  public static final IFileElementType PYTHON_REGEXP_FILE = new IFileElementType("PYTHON_REGEXP_FILE", PythonRegexpLanguage.INSTANCE);
  protected final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(RegExpCapability.DANGLING_METACHARACTERS,
                                                                      RegExpCapability.OCTAL_NO_LEADING_ZERO,
                                                                      RegExpCapability.OMIT_NUMBERS_IN_QUANTIFIERS);

  @Nonnull
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new RegExpLexer(CAPABILITIES);
  }

  @Override
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new RegExpParser(CAPABILITIES);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PYTHON_REGEXP_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, PythonRegexpLanguage.INSTANCE);
  }
}
