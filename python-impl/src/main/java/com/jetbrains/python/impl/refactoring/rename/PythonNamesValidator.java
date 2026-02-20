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

package com.jetbrains.python.impl.refactoring.rename;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.lexer.PythonLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PythonNamesValidator implements NamesValidator {

  @Override
  public boolean isKeyword(@Nonnull String name, Project project) {
    try {
      PythonLexer lexer = new PythonLexer();
      lexer.start(name);
      if (!PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens().contains(lexer.getTokenType())) {
        return false;
      }
      lexer.advance();
      return lexer.getTokenType() == null;
    }
    catch (StringIndexOutOfBoundsException e) {
      return false;
    }
  }

  @Override
  public boolean isIdentifier(@Nonnull String name, Project project) {
    try {
      PythonLexer lexer = new PythonLexer();
      lexer.start(name);
      if (lexer.getTokenType() != PyTokenTypes.IDENTIFIER) {
        return false;
      }
      lexer.advance();
      return lexer.getTokenType() == null;
    }
    catch (StringIndexOutOfBoundsException e) {
      return false;
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
