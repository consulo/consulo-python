/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.highlighting;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.console.parsing.PyConsoleHighlightingLexer;
import com.jetbrains.python.impl.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.impl.psi.PyUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.project.Project;
import consulo.util.collection.FactoryMap;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yole
 */
@ExtensionImpl
public class PySyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private final Map<LanguageLevel, PyHighlighter> myMap = new ConcurrentHashMap<>();

  @Override
  @Nonnull
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable final Project project, @Nullable final VirtualFile virtualFile) {
    final LanguageLevel level =
      project != null && virtualFile != null ? PyUtil.getLanguageLevelForVirtualFile(project, virtualFile) : LanguageLevel.getDefault();
    return myMap.computeIfAbsent(level, PyHighlighter::new);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
