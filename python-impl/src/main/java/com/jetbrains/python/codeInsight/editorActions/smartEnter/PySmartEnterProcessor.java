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

package com.jetbrains.python.codeInsight.editorActions.smartEnter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors.EnterProcessor;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors.PyCommentBreakerEnterProcessor;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors.PyPlainEnterProcessor;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers.*;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.action.SmartEnterProcessor;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   15:55:57
 */
@ExtensionImpl
public class PySmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance(PySmartEnterProcessor.class);
  private static final List<PyFixer> ourFixers = new ArrayList<PyFixer>();
  private static final List<EnterProcessor> ourProcessors = new ArrayList<EnterProcessor>();

  static {
    ourFixers.add(new PyStringLiteralFixer());
    ourFixers.add(new PyParenthesizedFixer());
    ourFixers.add(new PyMissingBracesFixer());
    ourFixers.add(new PyConditionalStatementPartFixer());
    ourFixers.add(new PyUnconditionalStatementPartFixer());
    ourFixers.add(new PyForPartFixer());
    ourFixers.add(new PyExceptFixer());
    ourFixers.add(new PyArgumentListFixer());
    ourFixers.add(new PyParameterListFixer());
    ourFixers.add(new PyFunctionFixer());
    ourFixers.add(new PyClassFixer());

    ourProcessors.add(new PyCommentBreakerEnterProcessor());
    ourProcessors.add(new PyPlainEnterProcessor());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }

  private static class TooManyAttemptsException extends Exception {
  }

  private static void collectAllElements(final PsiElement element, @Nonnull final List<PsiElement> result, boolean recurse) {
    result.add(0, element);
    if (doNotStep(element)) {
      if (!recurse) {
        return;
      }
      recurse = false;
    }

    final PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (element instanceof PyStatement && child instanceof PyStatement) {
        continue;
      }
      collectAllElements(child, result, recurse);
    }
  }

  private static boolean doNotStep(final PsiElement element) {
    return (element instanceof PyStatementList) || (element instanceof PyStatement);
  }

  private static boolean isModified(@Nonnull final Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    return editor.getDocument().getModificationStamp() != timestamp.longValue();
  }

  private int myFirstErrorOffset = Integer.MAX_VALUE;
  private static final int MAX_ATTEMPTS = 20;
  private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

  @Override
  public boolean process(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile psiFile) {
    final Document document = editor.getDocument();
    final String textForRollBack = document.getText();
    final int offset = editor.getCaretModel().getOffset();
    try {
      editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument().getModificationStamp());
      myFirstErrorOffset = Integer.MAX_VALUE;
      process(project, editor, psiFile, 0);
    }
    catch (TooManyAttemptsException e) {
      LOG.info(e);
      document.replaceString(0, document.getTextLength(), textForRollBack);
      editor.getCaretModel().moveToOffset(offset);
    }
    finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null);
    }
    return true;
  }

  private void process(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile psiFile, final int attempt)
    throws TooManyAttemptsException {
    if (attempt > MAX_ATTEMPTS) {
      throw new TooManyAttemptsException();
    }

    try {
      commit(editor);
      if (myFirstErrorOffset != Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }

      //myFirstErrorOffset = Integer.MAX_VALUE;

      PsiElement statementAtCaret = getStatementAtCaret(editor, psiFile);
      if (statementAtCaret == null) {
        if (!new PyCommentBreakerEnterProcessor().doEnter(editor, psiFile, false)) {
          SmartEnterUtil.plainEnter(editor);
        }
        return;
      }

      List<PsiElement> queue = new ArrayList<PsiElement>();
      collectAllElements(statementAtCaret, queue, true);
      queue.add(statementAtCaret);

      for (PsiElement element : queue) {
        for (PyFixer fixer : ourFixers) {
          fixer.apply(editor, this, element);
          if (LookupManager.getInstance(project).getActiveLookup() != null) {
            return;
          }
          if (isUncommited(project) || !element.isValid()) {
            process(project, editor, psiFile, attempt + 1);
            return;
          }
        }
      }

      doEnter(statementAtCaret, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void doEnter(final PsiElement atCaret, final Editor editor) {
    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      return;
    }
    commit(editor);

    for (EnterProcessor enterProcessor : ourProcessors) {
      if (enterProcessor.doEnter(editor, atCaret, isModified(editor))) {
        return;
      }
    }

    if (!isModified(editor)) {
      SmartEnterUtil.plainEnter(editor);
    }
    else {
      if (myFirstErrorOffset == Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(atCaret.getTextRange().getEndOffset());
      }
      else {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }
    }
  }

  @Nullable
  protected PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    PsiElement statementAtCaret = super.getStatementAtCaret(editor, psiFile);

    if (statementAtCaret instanceof PsiWhiteSpace) {
      return null;
    }
    if (statementAtCaret == null) {
      return null;
    }

    final PyStatementList statementList = PsiTreeUtil.getParentOfType(statementAtCaret, PyStatementList.class, false);
    if (statementList != null) {
      for (PyStatement statement : statementList.getStatements()) {
        if (PsiTreeUtil.isAncestor(statement, statementAtCaret, true)) {
          return statement;
        }
      }
    }
    else {
      final PyFile file = PsiTreeUtil.getParentOfType(statementAtCaret, PyFile.class, false);
      if (file != null) {
        for (PyStatement statement : file.getStatements()) {
          if (PsiTreeUtil.isAncestor(statement, statementAtCaret, true)) {
            return statement;
          }
        }
      }
    }
    return null;
  }

  public void registerUnresolvedError(int offset) {
    if (offset < myFirstErrorOffset) {
      myFirstErrorOffset = offset;
    }
  }
}
