// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstFStringFragment;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.GenericLineWrapPositionStrategy;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.LanguageLineWrapPositionStrategy;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;


@ExtensionImpl
public final class PyLineWrapPositionStrategy extends GenericLineWrapPositionStrategy implements LanguageLineWrapPositionStrategy {
  public PyLineWrapPositionStrategy() {
    // Commas.
    addRule(new Rule(',', WrapCondition.AFTER, Rule.DEFAULT_WEIGHT * 1.1));

    // Symbols to wrap either before or after.
    addRule(new Rule(' '));
    addRule(new Rule('\t'));

    // Symbols to wrap after.
    addRule(new Rule('(', WrapCondition.AFTER));
    addRule(new Rule('[', WrapCondition.AFTER));
    addRule(new Rule('{', WrapCondition.AFTER));
  }

  @Override
  protected boolean canUseOffset(Document document, int offset, boolean virtual) {
    if (virtual) {
      return true;
    }
    CharSequence text = document.getCharsSequence();
    char c = text.charAt(offset);
    if (!StringUtil.isWhiteSpace(c)) {
      return true;
    }

    int i = CharArrayUtil.shiftBackward(text, offset, " \t");
    if (i < 2) {
      return true;
    }
    return text.charAt(i - 2) != 'd' || text.charAt(i - 1) != 'e' || text.charAt(i) != 'f';
  }

  @Override
  public int calculateWrapPosition(Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean isSoftWrap) {

    int wrapPosition =
      super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset,
                                  isSoftWrap);
    if (wrapPosition < 0) return wrapPosition;
    final CharSequence text = document.getImmutableCharSequence();
    if (wrapPosition >= text.length()) return wrapPosition;

    if (project == null) {
      return wrapPosition;
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager != null) {
      final PsiFile psiFile = documentManager.getPsiFile(document);
      if (psiFile != null) {
        final PsiElement element = psiFile.findElementAt(wrapPosition);
        final StringLiteralExpression string = PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class);
        if (string != null) {
          final PyAstFStringFragment fragment = PsiTreeUtil.getTopmostParentOfType(element, PyAstFStringFragment.class);
          if (fragment != null) {
            return Math.max(fragment.getTextOffset(), startOffset);
          }

          if (wrapPosition > 0) {
            char charBefore = text.charAt(wrapPosition - 1);
            if (charBefore == '\'' || charBefore == '"') {
              //don't wrap the first char of string literal
              return wrapPosition + 1;
            }
          }

          char c = text.charAt(wrapPosition);
          if (StringUtil.isWhiteSpace(c)) {
            return wrapPosition + 1;
          }
        }
      }
    }
    return wrapPosition;
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
