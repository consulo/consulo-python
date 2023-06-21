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
package com.jetbrains.python.impl.editor;

import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.editor.PythonEnterHandler.DocstringState;
import com.jetbrains.python.psi.PyDocStringOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;

/**
 * User : catherine
 */
@ExtensionImpl
public class PythonSpaceHandler extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    if (c == ' ' && codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      if (element == null && offset > 1) {
        element = file.findElementAt(offset - 2);
      }
      if (element == null) {
        return Result.CONTINUE;
      }
      int expectedStringStart = offset - 4;        // """ or ''' plus space char
      final Document document = editor.getDocument();
      if (PythonEnterHandler.canGenerateDocstring(element, expectedStringStart, document) == DocstringState.INCOMPLETE) {
        final PyDocStringOwner docOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
        if (docOwner != null) {
          final String quotes = document.getText(TextRange.from(expectedStringStart, 3));
          final String docString = PyDocstringGenerator.forDocStringOwner(docOwner)
                                                       .forceNewMode()
                                                       .withInferredParameters(true)
                                                       .withQuotes(quotes)
                                                       .buildDocString();
          document.insertString(offset, docString.substring(3));
          if (!StringUtil.isEmptyOrSpaces(docString.substring(3, docString.length() - 3))) {
            editor.getCaretModel().moveCaretRelatively(100, 1, false, false, false);
          }
          return Result.STOP;
        }
      }
    }
    return Result.CONTINUE;
  }
}
