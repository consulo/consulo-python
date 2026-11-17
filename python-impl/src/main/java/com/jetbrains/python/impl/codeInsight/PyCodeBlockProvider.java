// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.action.CodeBlockProvider;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;

import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyCodeBlockProvider implements CodeBlockProvider {
  @Nullable
  @Override
  public TextRange getCodeBlockRange(Editor editor, PsiFile psiFile) {
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement element = psiFile.findElementAt(caretOffset);
    if (element == null) {
      return null;
    }
    while (caretOffset > 0 && element instanceof PsiWhiteSpace) {
      caretOffset--;
      element = psiFile.findElementAt(caretOffset);
    }
    PyStatement statement = PsiTreeUtil.getParentOfType(element, PyStatement.class);
    if (statement != null) {
      PyStatementList statementList = PsiTreeUtil.findChildOfType(statement, PyStatementList.class);

      // if the statement above caret is not a block statement, look above for a statement list and then find the statement above
      // that statement list
      if (statementList == null) {
        statementList = PsiTreeUtil.getParentOfType(statement, PyStatementList.class);
        if (statementList != null) {
          statement = PsiTreeUtil.getParentOfType(statementList, PyStatement.class);
        }
      }
      if (statement != null) {
        // if we're in the beginning of the statement already, pressing Ctrl-[ again should move the caret one statement higher
        int statementStart = statement.getTextRange().getStartOffset();
        int statementEnd = statement.getTextRange().getEndOffset();
        while (statementEnd > statementStart && psiFile.findElementAt(statementEnd) instanceof PsiWhiteSpace) {
          statementEnd--;
        }
        if (caretOffset == statementStart || caretOffset == statementEnd) {
          PyStatement statementAbove = PsiTreeUtil.getParentOfType(statement, PyStatement.class);
          if (statementAbove != null) {
            if (caretOffset == statementStart) {
              return new TextRange(statementAbove.getTextRange().getStartOffset(), statementEnd);
            }
            else {
              return new TextRange(statementStart, statementAbove.getTextRange().getEndOffset());
            }
          }
        }
        return statement.getTextRange();
      }
    }
    return null;
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
