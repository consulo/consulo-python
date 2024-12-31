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

package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.EvaluationMode;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;


public class PyDebuggerEditorsProvider extends XDebuggerEditorsProvider
{
  @Nonnull
  @Override
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Nonnull
  @Override
  public Document createDocument(@Nonnull final Project project,
                                 @Nonnull String text,
                                 @Nullable final XSourcePosition sourcePosition,
                                 @Nonnull EvaluationMode mode) {
    text = text.trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "fragment.py", text, true);

    // Bind to context
    final PsiElement element = getContextElement(project, sourcePosition);
    fragment.setContext(element);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Nullable
  private static PsiElement getContextElement(final Project project, XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      final Document document = FileDocumentManager.getInstance().getDocument(sourcePosition.getFile());
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        int offset = sourcePosition.getOffset();
        if (offset >= 0 && offset < document.getTextLength()) {
          final int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
          do {
            PsiElement element = psiFile.findElementAt(offset);
            if (element != null && !(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
              return PyPsiUtils.getStatement(element);
            }
            offset = element.getTextRange().getEndOffset() + 1;
          }
          while (offset < lineEndOffset);
        }
      }
    }
    return null;
  }
}
