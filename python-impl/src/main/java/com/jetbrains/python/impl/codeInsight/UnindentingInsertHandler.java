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

package com.jetbrains.python.impl.codeInsight;

import consulo.codeEditor.action.EditorActionUtil;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.codeInsight.completion.PythonLookupElement;
import com.jetbrains.python.psi.PyStatementWithElse;
import com.jetbrains.python.psi.PyTryExceptStatement;

/**
 * Adjusts indentation after a final part keyword is inserted, e.g. an "else:".
 * User: dcheryasov
 * Date: Mar 2, 2010 6:48:40 PM
 */
public class UnindentingInsertHandler implements InsertHandler<PythonLookupElement> {
  public final static UnindentingInsertHandler INSTANCE = new UnindentingInsertHandler();

  private UnindentingInsertHandler() {
  }

  public void handleInsert(InsertionContext context, PythonLookupElement item) {
    unindentAsNeeded(context.getProject(), context.getEditor(), context.getFile());
  }

  /**
   * Unindent current line to be flush with a starting part, detecting the part if necessary.
   * @param project
   * @param editor
   * @param file
   * @return true if unindenting succeeded
   */
  public static boolean unindentAsNeeded(Project project, Editor editor, PsiFile file) {
    // TODO: handle things other than "else"
    final Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    CharSequence text = document.getCharsSequence();
    if (offset >= text.length()) offset = text.length() - 1;

    int line_start_offset = document.getLineStartOffset(document.getLineNumber(offset));
    int nonspace_offset = findBeginning(line_start_offset, text);


    Class<? extends PsiElement> parentClass = null;

    int last_offset = nonspace_offset + "finally".length(); // the longest of all
    if (last_offset > offset) last_offset = offset;
    int local_length = last_offset - nonspace_offset + 1;
    if (local_length > 0) {
      String piece = text.subSequence(nonspace_offset, last_offset+1).toString();
      final int else_len = "else".length();
      if (local_length >= else_len) {
        if ((piece.startsWith("else") || piece.startsWith("elif")) && (else_len == piece.length() || piece.charAt(else_len) < 'a' || piece.charAt(else_len) > 'z')) {
          parentClass = PyStatementWithElse.class;
        }
      }
      final int except_len = "except".length();
      if (local_length >= except_len) {
        if (piece.startsWith("except") && (except_len == piece.length() || piece.charAt(except_len) < 'a' || piece.charAt(except_len) > 'z')) {
          parentClass = PyTryExceptStatement.class;
        }
      }
      final int finally_len = "finally".length();
      if (local_length >= finally_len) {
        if (piece.startsWith("finally") && (finally_len == piece.length() || piece.charAt(finally_len) < 'a' || piece.charAt(finally_len) > 'z')) {
          parentClass = PyTryExceptStatement.class;
        }
      }
    }


    if (parentClass == null) return false; // failed

    PsiDocumentManager.getInstance(project).commitDocument(document); // reparse

    PsiElement token = file.findElementAt(offset-2); // -1 is our ':'; -2 is even safer.
    PsiElement outer = PsiTreeUtil.getParentOfType(token, parentClass);
    if (outer != null) {
      int outer_offset = outer.getTextOffset();
      int outer_indent = outer_offset - document.getLineStartOffset(document.getLineNumber(outer_offset));
      assert outer_indent >= 0;
      int current_indent = nonspace_offset - line_start_offset;
      EditorActionUtil.indentLine(project, editor, document.getLineNumber(offset), outer_indent - current_indent);
      return true;
    }
    return false;
  }


  // finds offset of first non-space in the line
  private static int findBeginning(int start_offset, CharSequence text) {
    int current_offset = start_offset;
    int text_length = text.length();
    while (current_offset < text_length) {
      char current_char = text.charAt(current_offset);
      if (current_char != ' ' && current_char != '\t' && current_char != '\n') break;
      current_offset += 1;
    }
    return current_offset;
  }
}
