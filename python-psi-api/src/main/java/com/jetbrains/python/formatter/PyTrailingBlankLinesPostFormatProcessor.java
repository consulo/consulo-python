// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.impl.PyUtilCore;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.PersistentEditorSettings;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.PostFormatProcessor;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

/**
 * Handles extra blank lines at the end of the file if corresponding whitespace elements belong to formatted range/element.
 * These trailing whitespaces are replaced by line feeds if either:
 * <ul>
 * <li>Option {@link PyCodeStyleSettings#BLANK_LINE_AT_FILE_END} is enabled.</li>
 * <li>Setting {@link PersistentEditorSettings#isEnsureNewLineAtEOF()} is enabled. Otherwise extra new line added on the next
 * "Save" action will be removed after reformatting.</li>
 * </ul>
 * <em>and</em> file is not empty.
 * If none of these conditions holds, blank lines are removed completely.
 *
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class PyTrailingBlankLinesPostFormatProcessor implements PostFormatProcessor {

  private static boolean isApplicableTo(PsiFile source) {
    if (InjectedLanguageManager.getInstance(source.getProject()).isInjectedFragment(source)) {
      return false;
    }
    return source.getLanguage().isKindOf(PythonLanguage.getInstance());
  }

  @Override
  public PsiElement processElement(PsiElement source, CodeStyleSettings settings) {
    final PsiFile psiFile = source.getContainingFile();
    if (isApplicableTo(psiFile)) {
      final TextRange whitespaceRange = findTrailingWhitespacesRange(psiFile);
      if (source.getTextRange().intersects(whitespaceRange)) {
        replaceOrDeleteTrailingWhitespaces(psiFile, whitespaceRange);
      }
    }
    return source;
  }

  @Override
  public TextRange processText(PsiFile source, TextRange rangeToReformat, CodeStyleSettings settings) {
    if (!isApplicableTo(source)) {
      return rangeToReformat;
    }
    final TextRange oldWhitespaceRange = findTrailingWhitespacesRange(source);
    if (rangeToReformat.intersects(oldWhitespaceRange)) {
      final TextRange newWhitespaceRange = replaceOrDeleteTrailingWhitespaces(source, oldWhitespaceRange);
      return rangeToReformat.grown(oldWhitespaceRange.getLength() - newWhitespaceRange.getLength());
    }
    return rangeToReformat;
  }

  private static TextRange findTrailingWhitespacesRange(PsiFile file) {
    final CharSequence contents = file.getViewProvider().getContents();
    int start;
    boolean lineFeedNext = false;
    for (start = contents.length() - 1; start >= 0; start--) {
      final char c = contents.charAt(start);
      if (" \t\f\n\r".indexOf(c) < 0 && !(c == '\\' && (lineFeedNext || start == contents.length() - 1))) {
        break;
      }
      lineFeedNext = c == '\n';
    }
    return new TextRange(start + 1, contents.length());
  }

  private static TextRange replaceOrDeleteTrailingWhitespaces(final PsiFile pyFile, final TextRange whitespaceRange) {
    final PyCodeStyleSettings customSettings = CodeStyle.getCustomSettings(pyFile, PyCodeStyleSettings.class);
    final boolean addLineFeed = customSettings.BLANK_LINE_AT_FILE_END || PersistentEditorSettings.getInstance().isEnsureNewLineAtEOF();

    final String realWhitespace = whitespaceRange.substring(pyFile.getText());
    final String desiredWhitespace = addLineFeed ? "\n" : "";

    // Do not add extra blank line in an empty file
    if (!realWhitespace.equals(desiredWhitespace) && (whitespaceRange.getStartOffset() != 0 || desiredWhitespace.isEmpty())) {
      TextRange updatedRange = PyUtilCore.updateDocumentUnblockedAndCommitted(pyFile, document -> {
        document.replaceString(whitespaceRange.getStartOffset(), whitespaceRange.getEndOffset(), desiredWhitespace);
        return TextRange.from(whitespaceRange.getStartOffset(), desiredWhitespace.length());
      });
      if (updatedRange != null) {
        return updatedRange;
      }
    }
    return whitespaceRange;
  }
}
