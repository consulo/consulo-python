// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.PythonDocumentationHighlightingService;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.application.util.HtmlChunk;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

public final class PyDocSignaturesHighlighterKt {
  private PyDocSignaturesHighlighterKt() {
  }

  public static HtmlChunk styledSpan(String text, TextAttributesKey textAttributeKey) {
    return HtmlChunk.raw(styledSpanWithRawText(HtmlChunk.text(text).toString(), textAttributeKey));
  }

  public static HtmlChunk styledSpan(HtmlChunk text, TextAttributesKey textAttributeKey) {
    return HtmlChunk.raw(styledSpanWithRawText(text.toString(), textAttributeKey));
  }

  public static HtmlChunk highlightExpressionText(String expressionText, @Nullable PyExpression expression) {
    if (expression == null) {
      return HtmlChunk.text(expressionText);
    }
    // Special case for PyStringLiteralExpression there because HtmlSyntaxInfoUtil will consider expressionText as docstring.
    // (The only string literal in a file is considered its docstring).
    else if (expression instanceof PyStringLiteralExpression) {
      return styledSpan(expressionText, PyHighlighter.PY_UNICODE_STRING);
    }
    else {
      return HtmlChunk.raw(PythonDocumentationHighlightingService.getInstance().highlightedCodeSnippet(expression.getProject(), expressionText));
    }
  }

  private static String styledSpanWithRawText(String text, TextAttributesKey textAttributeKey) {
    return PythonDocumentationHighlightingService.getInstance().styledSpan(textAttributeKey, text);
  }

  public static TextAttributesKey paramNameTextAttribute(boolean isSelf) {
    return isSelf ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
  }

  public static TextAttributesKey functionNameTextAttribute(PyFunction function, @Nullable String funcName) {
    LanguageLevel languageLevel = LanguageLevel.forElement(function);
    if ((PyNames.UNDERSCORED_ATTRIBUTES.contains(funcName) || PyNames.getBuiltinMethods(languageLevel).containsKey(funcName))
        && function.getContainingClass() != null) {
      return PyHighlighter.PY_PREDEFINED_USAGE;
    }
    return PyHighlighter.PY_FUNC_DEFINITION;
  }

  public static HtmlChunk styledReference(HtmlChunk refText, PsiElement refTarget) {
    if (PyBuiltinCache.getInstance(refTarget).isBuiltin(refTarget)) {
      return styledSpan(refText, PyHighlighter.PY_BUILTIN_NAME);
    }
    return refText;
  }
}
