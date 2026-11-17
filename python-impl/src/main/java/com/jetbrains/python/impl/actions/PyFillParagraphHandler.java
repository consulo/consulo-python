// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.action.ParagraphFillHandler;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.CharFilter;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * @author ktisha
 */
@ExtensionImpl
public class PyFillParagraphHandler extends ParagraphFillHandler {
  @Override
  @RequiredReadAction
  protected String getPrefix(PsiElement element) {
    PyStringLiteralExpression stringLiteralExpression = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      String text = stringLiteralExpression.getText();
      Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(text);
      PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyDocStringOwner.class);
      if (docStringOwner != null && stringLiteralExpression.equals(docStringOwner.getDocStringExpression())) {
        String indent = getIndent(stringLiteralExpression);
        if (quotes != null) {
          List<String> strings = StringUtil.split(text, "\n");
          if (strings.get(0).trim().equals(quotes.getFirst())) {
            return quotes.getFirst() + indent;
          }
          else {
            String value = stringLiteralExpression.getStringValue();
            int firstNotSpace = StringUtil.findFirst(value, CharFilter.NOT_WHITESPACE_FILTER);
            return quotes.getFirst() + value.substring(0, firstNotSpace);
          }
        }
        return "\"" + indent;
      }
      else
        return quotes != null ? quotes.getFirst() : "\"";
    }
    return element instanceof PsiComment ? "# " : "";
  }

  @RequiredReadAction
  private static String getIndent(PyStringLiteralExpression stringLiteralExpression) {
    PyStatementList statementList = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyStatementList.class);
    String indent = "";
    if (statementList != null) {
      indent = statementList.getPrevSibling() instanceof PsiWhiteSpace whiteSpace ? whiteSpace.getText() : "\n";
    }
    return indent;
  }

  @Override
  @RequiredReadAction
  protected String getPostfix(PsiElement element) {
    PyStringLiteralExpression stringLiteralExpression = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      String text = stringLiteralExpression.getText();
      Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(text);
      PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyDocStringOwner.class);
      if (docStringOwner != null && stringLiteralExpression.equals(docStringOwner.getDocStringExpression())) {
        String indent = getIndent(stringLiteralExpression);
        if (quotes != null) {
          List<String> strings = StringUtil.split(text, "\n");
          if (strings.get(strings.size() - 1).trim().equals(quotes.getSecond())) {
            return indent + quotes.getSecond();
          }
          else {
            return quotes.getSecond();
          }
        }
        return indent + "\"";
      }
      else
        return quotes != null ? quotes.getSecond() : "\"";
    }
    return "";
  }

  @Override
  public boolean isAvailableForElement(@Nullable PsiElement element) {
    if (element != null) {
      PyStringLiteralExpression stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
      return stringLiteral != null || element instanceof PsiComment;
    }
    return false;
  }

  @Override
  public boolean isAvailableForFile(@Nullable PsiFile psiFile) {
    return psiFile instanceof PyFile;
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
