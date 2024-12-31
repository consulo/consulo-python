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

package com.jetbrains.python.impl.codeInsight.imports;

import jakarta.annotation.Nonnull;

import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.HintAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;

/**
 * @author yole
 */
public class AutoImportHintAction implements LocalQuickFix, HintAction, HighPriorityAction {
  private final AutoImportQuickFix myDelegate;

  public AutoImportHintAction(AutoImportQuickFix delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean showHint(@Nonnull Editor editor) {
    return myDelegate.showHint(editor);
  }

  @Nonnull
  @Override
  public String getText() {
    return myDelegate.getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myDelegate.isAvailable();
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
  {
    myDelegate.invoke(file);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nonnull
  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return myDelegate.getFamilyName();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    myDelegate.applyFix(project, descriptor);
  }
}
