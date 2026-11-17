// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

import java.util.List;

/**
 * Looks for well-formedness of string constants.
 */
final class PyStringLiteralQuotesAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyStringLiteralQuotesAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    private static final String TRIPLE_QUOTES = "\"\"\"";
    private static final String TRIPLE_APOS = "'''";

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
        List<ASTNode> stringNodes = node.getStringNodes();
        for (ASTNode stringNode : stringNodes) {
            // TODO Migrate to newer PyStringElement API
            if (stringNode.getElementType() == PyElementTypes.FSTRING_NODE) {
                continue;
            }
            String nodeText = PyPsiUtils.getElementTextWithoutHostEscaping(stringNode.getPsi());
            int index = PyStringLiteralUtil.getPrefixLength(nodeText);
            String unprefixed = nodeText.substring(index);
            boolean foundError;
            if (StringUtil.startsWith(unprefixed, TRIPLE_QUOTES)) {
                foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_QUOTES);
            }
            else if (StringUtil.startsWith(unprefixed, TRIPLE_APOS)) {
                foundError = checkTripleQuotedString(stringNode, unprefixed, TRIPLE_APOS);
            }
            else {
                foundError = checkQuotedString(stringNode, unprefixed);
            }
            if (foundError) {
                break;
            }
        }
    }

    private boolean checkQuotedString(ASTNode stringNode, String nodeText) {
        char firstQuote = nodeText.charAt(0);
        char lastChar = nodeText.charAt(nodeText.length() - 1);
        int precedingBackslashCount = 0;
        for (int i = nodeText.length() - 2; i >= 0; i--) {
            if (nodeText.charAt(i) == '\\') {
                precedingBackslashCount++;
            }
            else {
                break;
            }
        }
        if (nodeText.length() == 1 || lastChar != firstQuote || precedingBackslashCount % 2 != 0) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annMissingClosingQuote(firstQuote).get()).range(stringNode)
                .create();
            return true;
        }
        return false;
    }

    private boolean checkTripleQuotedString(ASTNode stringNode, String text, String quotes) {
        if (text.length() < 6 || !text.endsWith(quotes)) {
            int startOffset = StringUtil.trimTrailing(stringNode.getText()).lastIndexOf('\n');
            if (startOffset < 0) {
                startOffset = stringNode.getTextRange().getStartOffset();
            }
            else {
                startOffset = stringNode.getTextRange().getStartOffset() + startOffset + 1;
            }
            TextRange highlightRange = new TextRange(startOffset, stringNode.getTextRange().getEndOffset());
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annMissingClosingTripleQuotes().get()).range(highlightRange)
                .create();
            return true;
        }
        return false;
    }
}
