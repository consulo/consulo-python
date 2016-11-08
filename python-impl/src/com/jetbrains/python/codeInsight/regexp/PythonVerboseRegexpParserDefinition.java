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

import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParser;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import consulo.lang.LanguageVersion;

/**
 * @author yole
 */
public class PythonVerboseRegexpParserDefinition extends PythonRegexpParserDefinition {
  public static final IFileElementType VERBOSE_PYTHON_REGEXP_FILE = new IFileElementType("VERBOSE_PYTHON_REGEXP_FILE", PythonVerboseRegexpLanguage.INSTANCE);
  private final EnumSet<RegExpCapability> VERBOSE_CAPABILITIES;

  public PythonVerboseRegexpParserDefinition() {
    VERBOSE_CAPABILITIES = EnumSet.copyOf(CAPABILITIES);
    VERBOSE_CAPABILITIES.add(RegExpCapability.COMMENT_MODE);
  }

  @NotNull
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new RegExpLexer(VERBOSE_CAPABILITIES);
  }

  @Override
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new RegExpParser(VERBOSE_CAPABILITIES);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return VERBOSE_PYTHON_REGEXP_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, PythonVerboseRegexpLanguage.INSTANCE);
  }
}
