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

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.ide.impl.idea.codeStyle.CodeStyleFacade;
import consulo.document.Document;
import consulo.ide.impl.idea.openapi.editor.actions.ConvertIndentsActionBase;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;

/**
 * @author yole
 */
public class ConvertIndentsFix implements LocalQuickFix {
  private final boolean myToSpaces;

  public ConvertIndentsFix(boolean toSpaces) {
    myToSpaces = toSpaces;
  }

  @Nonnull
  @Override
  public String getName() {
    return myToSpaces ? "Convert indents to spaces" : "Convert indents to tabs";
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return "Convert indents";
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      int tabSize = CodeStyleFacade.getInstance(project).getIndentSize(file.getFileType());
      TextRange allDoc = new TextRange(0, document.getTextLength());
      if (myToSpaces) {
        ConvertIndentsActionBase.convertIndentsToSpaces(document, tabSize, allDoc);
      }
      else {
        ConvertIndentsActionBase.convertIndentsToTabs(document, tabSize, allDoc);
      }
    }
  }
}
