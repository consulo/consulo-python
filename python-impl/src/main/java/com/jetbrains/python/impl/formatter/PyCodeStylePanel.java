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

package com.jetbrains.python.impl.formatter;

import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractPanel;
import consulo.language.editor.highlight.HighlighterFactory;
import consulo.colorScheme.EditorColorsScheme;
import consulo.codeEditor.EditorHighlighter;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.psi.PsiFile;
import com.jetbrains.python.impl.highlighting.PyHighlighter;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.LanguageLevel;

import javax.annotation.Nonnull;

import javax.swing.*;

/**
 * @author yole
 */
public class PyCodeStylePanel extends CodeStyleAbstractPanel
{
  private JPanel myPanel;

  protected PyCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return HighlighterFactory.createHighlighter(new PyHighlighter(LanguageLevel.PYTHON26), scheme);
  }

  @Override
  protected int getRightMargin() {
    return 80;
  }

  @Override
  protected void prepareForReformat(PsiFile psiFile) {
  }

  @Nonnull
  @Override
  protected FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return "";
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
  }

  @Override
  public void apply(CodeStyleSettings settings) {
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return false;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }
}
