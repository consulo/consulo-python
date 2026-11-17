// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

/**
 * @author Mikhail Golubev
 */
final class PyFStringsAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyFStringsAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyFStringFragment(PyFStringFragment node) {
        PsiElement typeConversion = node.getTypeConversion();
        if (typeConversion != null) {
            String conversionChar = typeConversion.getText().substring(1);
            if (conversionChar.isEmpty()) {
                report(typeConversion, PyLocalize.annFstringsMissingConversionCharacter().get());
            }
            else if (conversionChar.length() > 1 || "sra".indexOf(conversionChar.charAt(0)) < 0) {
                report(typeConversion, PyLocalize.annFstringsIllegalConversionCharacter(conversionChar).get());
            }
        }
    }

    @Override
    public void visitPyFormattedStringElement(PyFormattedStringElement node) {
        String wholeNodeText = node.getText();
        for (TextRange textRange : node.getLiteralPartRanges()) {
            int i = textRange.getStartOffset();
            while (i < textRange.getEndOffset()) {
                int nextOffset = skipNamedUnicodeEscape(wholeNodeText, i, textRange.getEndOffset());
                if (i != nextOffset) {
                    i = nextOffset;
                    continue;
                }
                char c = wholeNodeText.charAt(i);
                if (c == '}') {
                    if (i + 1 < textRange.getEndOffset() && wholeNodeText.charAt(i + 1) == '}') {
                        i += 2;
                        continue;
                    }
                    reportCharacter(node, i, PyLocalize.annFstringsSingleRightBraceNotAllowedInsideFstrings().get());
                }
                i++;
            }
        }
    }

    private static int skipNamedUnicodeEscape(String nodeText, int offset, int endOffset) {
        if (StringUtil.startsWith(nodeText, offset, "\\N{")) {
            int rightBraceOffset = nodeText.indexOf('}', offset + 3);
            return rightBraceOffset < 0 ? endOffset : rightBraceOffset + 1;
        }
        return offset;
    }

    public void reportCharacter(PsiElement element, int offset, String message) {
        int nodeStartOffset = element.getTextRange().getStartOffset();
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(TextRange.from(offset, 1).shiftRight(nodeStartOffset)).create();
    }

    public void report(PsiElement element, String error) {
        myHolder.newAnnotation(HighlightSeverity.ERROR, error).range(element).create();
    }
}
