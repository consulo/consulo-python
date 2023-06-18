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

package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonLanguage;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyLineWrapPositionStrategy extends GenericLineWrapPositionStrategy implements LanguageLineWrapPositionStrategy {
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

    // Symbols to wrap before
    addRule(new Rule('.', WrapCondition.BEFORE));
  }

  @Override
  protected boolean canUseOffset(@Nonnull Document document, int offset, boolean virtual) {
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
  public int calculateWrapPosition(@Nonnull Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean virtual) {
    int wrapPosition =
      super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset, virtual);
    if (wrapPosition < 0) return wrapPosition;
    final CharSequence text = document.getCharsSequence();

    char c = text.charAt(wrapPosition);
    if (!StringUtil.isWhiteSpace(c) || project == null) {
      return wrapPosition;
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager != null) {
      final PsiFile psiFile = documentManager.getPsiFile(document);
      if (psiFile != null) {
        final PsiElement element = psiFile.findElementAt(wrapPosition);
        final StringLiteralExpression string = PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class);

        if (string != null) {
          return wrapPosition + 1;
        }
      }
    }
    return wrapPosition;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
