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

package com.jetbrains.python.inspections.quickfix;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public class ReformatFix implements IntentionAction, LocalQuickFix, HighPriorityAction {
  @Nonnull
  @Override
  public String getText() {
    return "Reformat file";
  }

  @Nonnull
  @Override
  public String getName() {
    return getText();
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    CodeStyleManager.getInstance(project).reformat(descriptor.getPsiElement().getContainingFile());
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }
    CodeStyleManager.getInstance(project).reformat(file);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
