// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import java.util.Collection;
import static com.jetbrains.python.impl.psi.PyUtil.as;

public final class PyQuotesUtil {

  private PyQuotesUtil() {
  }

  public static boolean canBeConverted(PyStringElement stringElement, boolean checkContainingFString) {
    if (stringElement.isTripleQuoted() || !stringElement.isTerminated()) return false;
    if (checkContainingFString) {
      PyFormattedStringElement parentFString = PsiTreeUtil.getParentOfType(stringElement, PyFormattedStringElement.class, true,
                                                                           PyStatement.class);
      char targetQuote = PyStringLiteralUtil.flipQuote(stringElement.getQuote().charAt(0));
      if (parentFString != null && LanguageLevel.forElement(stringElement).isOlderThan(LanguageLevel.PYTHON312)) {
        boolean parentFStringUsesTargetQuotes = parentFString.getQuote().equals(Character.toString(targetQuote));
        if (parentFStringUsesTargetQuotes) return false;
        boolean conversionIntroducesBackslashEscapedQuote = stringElement.textContains(targetQuote);
        if (conversionIntroducesBackslashEscapedQuote) return false;
      }
    }
    PyFormattedStringElement fStringElement = as(stringElement, PyFormattedStringElement.class);
    if (fStringElement != null) {
      Collection<PyStringElement> innerStrings = PsiTreeUtil.findChildrenOfType(fStringElement, PyStringElement.class);
      if (ContainerUtil.exists(innerStrings, s -> !canBeConverted(s, false))) {
        return false;
      }
    }
    return true;
  }

  public static PyStringElement createCopyWithConvertedQuotes(PyStringElement element) {
    StringBuilder builder = new StringBuilder();
    processStringElement(builder, element);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
    return (PyStringElement)elementGenerator.createStringLiteralAlreadyEscaped(builder.toString()).getFirstChild();
  }

  private static void processStringElement(StringBuilder builder, PyStringElement stringElement) {
    char originalQuote = stringElement.getQuote().charAt(0);
    if (stringElement instanceof PyPlainStringElement) {
      processStringElementText(builder, stringElement.getText(), originalQuote);
    }
    else {
      boolean fStringCanIncludeArbitraryStringLiterals = LanguageLevel.forElement(stringElement).isAtLeast(LanguageLevel.PYTHON312);
      stringElement.acceptChildren(new PyRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof PyStringElement) {
            if (fStringCanIncludeArbitraryStringLiterals) {
              builder.append(element.getText());
            }
            else {
              processStringElement(builder, (PyStringElement)element);
            }
          }
          else if (PyTokenTypes.FSTRING_TOKENS.contains(element.getNode().getElementType())) {
            processStringElementText(builder, element.getText(), originalQuote);
          }
          else if (element.getNode().getChildren(null).length == 0) {
            builder.append(element.getText());
          }
          else {
            super.visitElement(element);
          }
        }
      });
    }
  }

  private static void processStringElementText(StringBuilder builder, String stringText, char originalQuote) {
    char targetQuote = PyStringLiteralUtil.flipQuote(originalQuote);
    char[] charArr = stringText.toCharArray();
    int i = 0;
    while (i != charArr.length) {
      char ch1 = charArr[i];
      char ch2 = i + 1 < charArr.length ? charArr[i + 1] : '\0';
      if (ch1 == originalQuote) {
        builder.append(targetQuote);
      }
      else if (ch1 == targetQuote) {
        builder.append("\\").append(targetQuote);
      }
      else if (ch1 == '\\') {
        if (ch2 == originalQuote) {
          builder.append(ch2);
          i++;
        }
        else if (ch2 == '\0') {
          builder.append(ch1);
        }
        else {
          builder.append(ch1).append(ch2);
          i++;
        }
      }
      else {
        builder.append(ch1);
      }
      i++;
    }
  }
}
