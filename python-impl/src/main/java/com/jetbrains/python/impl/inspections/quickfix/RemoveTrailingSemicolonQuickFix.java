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
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;

/**
 * @since Alexey.Ivanov
 * @since 2009-07-30
 */
public class RemoveTrailingSemicolonQuickFix implements LocalQuickFix {
  @Nonnull
  public LocalizeValue getName() {
    return PyLocalize.qfixRemoveTrailingSemicolon();
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if ((problemElement != null) && (";".equals(problemElement.getText()))) {

      if (!FileModificationService.getInstance().preparePsiElementForWrite(problemElement)) {
        return;
      }
      problemElement.delete();
    }
  }
}
