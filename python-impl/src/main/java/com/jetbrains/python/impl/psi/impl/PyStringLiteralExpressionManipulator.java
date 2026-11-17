// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;

@ExtensionImpl
public final class PyStringLiteralExpressionManipulator extends AbstractElementManipulator<PyStringLiteralExpressionImpl> {

    @Override
    public PyStringLiteralExpressionImpl handleContentChange(PyStringLiteralExpressionImpl element,
                                                             TextRange range,
                                                             String newContent) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        String escapedText = calculateEscapedText(element.getText(), range, newContent);

        PyStringLiteralExpression escaped = elementGenerator.createStringLiteralAlreadyEscaped(escapedText);

        return (PyStringLiteralExpressionImpl)element.replace(escaped);
    }

    @Override
    public PyStringLiteralExpressionImpl handleContentChange(PyStringLiteralExpressionImpl element, String newContent)
        throws IncorrectOperationException {
        return handleContentChange(element, TextRange.create(0, element.getTextLength()), newContent);
    }

    @Override
    public TextRange getRangeInElement(PyStringLiteralExpressionImpl element) {
        return element.getStringValueTextRange();
    }

    private static String calculateEscapedText(String prevText,
                                                        TextRange range,
                                                        String newContent) {
        String newText = range.replace(prevText, newContent);

        if (PyStringLiteralCoreUtil.isQuoted(newText)) {
            return newText;
        }

        Pair<String, String> quotes = calculateQuotes(prevText);
        return quotes.first + newText + quotes.second;
    }

    private static Pair<String, String> calculateQuotes(String text) {
        Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(text);

        if (quotes == null || quotes.first == null && quotes.second == null) {
            return Pair.createNonNull("\"", "\"");
        }

        if (quotes.first == null) {
            return Pair.createNonNull(quotes.second, quotes.second);
        }
        if (quotes.second == null) {
            return Pair.createNonNull(quotes.first, quotes.first);
        }

        return quotes;
    }

    @Override
    public Class<PyStringLiteralExpressionImpl> getElementClass() {
        return PyStringLiteralExpressionImpl.class;
    }
}
