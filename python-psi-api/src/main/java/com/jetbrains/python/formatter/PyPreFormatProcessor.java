// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstRecursiveElementVisitor;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAstElementGenerator;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.PreFormatProcessor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.project.Project;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class PyPreFormatProcessor implements PreFormatProcessor {
  @Override
  public TextRange process(ASTNode element, TextRange range) {
    PsiElement psiElement = element.getPsi();
    if (psiElement == null) return range;

    if (!psiElement.getLanguage().is(PythonLanguage.getInstance())) return range;

    PyPsiUtilsCore.assertValid(psiElement);

    PsiFile file = psiElement.isValid() ? psiElement.getContainingFile() : null;
    if (file == null) return range;

    return new PyCommentFormatter(file).process(psiElement, range);
  }

  public static class PyCommentFormatter extends PyAstRecursiveElementVisitor {
    private final Project myProject;
    private final PyCodeStyleSettings myPyCodeStyleSettings;
    private final List<Couple<PsiComment>> myCommentReplacements = new ArrayList<>();
    private TextRange myRange;
    private int myDelta = 0;

    public PyCommentFormatter(PsiFile file) {
      myProject = file.getProject();
      myPyCodeStyleSettings = CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class);
    }

    public TextRange process(PsiElement element, TextRange range) {
      if (!myPyCodeStyleSettings.SPACE_AFTER_NUMBER_SIGN) {
        return range;
      }
      myRange = range;
      final PsiDocumentManager manager = PsiDocumentManager.getInstance(myProject);
      final Document document = manager.getDocument(element.getContainingFile());
      if (document != null) {
        // collect all comments
        element.accept(this);
        for (Couple<PsiComment> pair : myCommentReplacements) {
          pair.getFirst().replace(pair.getSecond());
        }
      }
      return TextRange.create(range.getStartOffset(), range.getEndOffset() + myDelta);
    }

    @Override
    public void visitComment(PsiComment comment) {
      if (!myRange.contains(comment.getTextRange())) {
        return;
      }
      final String origText = comment.getText();
      final int commentStart = origText.indexOf('#');
      if (commentStart != -1 && (commentStart + 1) < origText.length()) {
        final char charAfterDash = origText.charAt(commentStart + 1);
        if (charAfterDash == '!' && comment.getTextRange().getStartOffset() == 0) {
          return; // shebang
        }
        if (charAfterDash == '#' || charAfterDash == ':') {
          return; // doc comment
        }
        final String commentTextWithoutDash = origText.substring(commentStart + 1);
        final String newText;
        if (isTrailingComment(comment)) {
          newText = "# " + StringUtil.trimLeading(commentTextWithoutDash);
        }
        else if (!StringUtil.isWhiteSpace(charAfterDash)) {
          newText = "# " + commentTextWithoutDash;
        }
        else {
          return;
        }
        if (!newText.equals(origText)) {
          myDelta += newText.length() - origText.length();
          final PyAstElementGenerator elementGenerator = PyAstElementGenerator.getInstance(myProject);
          final PsiComment newComment = elementGenerator.createFromText(LanguageLevel.forElement(comment), PsiComment.class, newText);
          myCommentReplacements.add(Couple.of(comment, newComment));
        }
      }
    }
  }

  private static boolean isTrailingComment(PsiComment comment) {
    final PsiElement prevElement = comment.getPrevSibling();
    return prevElement != null && (!(prevElement instanceof PsiWhiteSpace) || !prevElement.textContains('\n'));
  }

  @Override
  public boolean changesWhitespacesOnly() {
    return true;
  }
}
