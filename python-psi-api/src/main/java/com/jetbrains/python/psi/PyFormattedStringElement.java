// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.psi;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByType;
import static com.jetbrains.python.ast.PyAstElementKt.findChildByTypeNotNull;
import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByType;

/**
 * Represents an f-string, a special kind of interpolated string literal introduced in Python 3.6.
 * <p>
 * Unlike {@link PyPlainStringElement} these elements are composite and consist of several kinds of tokens
 * and normal PSI trees for embedded expressions.
 *
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_START
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_TEXT
 * @see PyFStringFragment
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_END
 */
// TODO consulo: JB extends com.jetbrains.python.ast.PyAstFormattedStringElement (python-ast layer not ported),
//  its default methods are placed here
public interface PyFormattedStringElement extends PyStringElement, PyElement {
    /**
     * Returns a list of replacement fields containing expressions which values should be embedded into this literal content.
     * <p>
     * Note that only top-level fields are included in the result. To access optional fragments that might appear in
     * format specifier of a another fragment one should use {@link PyFStringFragment#getFormatPart()} and
     * {@link PyFStringFragmentFormatPart#getFragments()}.
     */
    default List<PyFStringFragment> getFragments() {
        return findChildrenByType(this, PyElementTypes.FSTRING_FRAGMENT);
    }

    /**
     * Returns ranges of literal parts of an f-string, e.g. all other parts of an f-string content that don't belong to
     * expression fragments.
     * <p>
     * These ranges don't include literal format specifier parts of fragments and should be completely
     * covered by {@link PyStringElement#getContentRange()}.
     * <p>
     * For instance, for the following f-string:
     * <p>
     * <pre>{@code
     *   f'foo{bar:format}baz'
     * }</pre>
     * this method will return ranges {@code (2, 5)} and {@code (17, 20)}.
     */
    @RequiredReadAction
    default List<TextRange> getLiteralPartRanges() {
        List<PsiElement> textTokens = findChildrenByType(this, PyTokenTypes.FSTRING_TEXT_TOKENS);
        return ContainerUtil.map(textTokens, PsiElement::getTextRangeInParent);
    }

    @Override
    @RequiredReadAction
    default TextRange getContentRange() {
        TextRange textRange = getTextRange();
        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();

        PsiElement startToken = findChildByTypeNotNull(this, PyTokenTypes.FSTRING_START);
        PsiElement endToken = findChildByType(this, PyTokenTypes.FSTRING_END);
        TextRange absoluteRange = TextRange.create(startToken.getTextRange().getEndOffset(),
            endToken != null ? endToken.getTextRange().getStartOffset() : endOffset);
        return absoluteRange.shiftLeft(startOffset);
    }

    @Override
    @RequiredReadAction
    default List<Pair<TextRange, String>> getDecodedFragments() {
        List<Pair<TextRange, String>> result = new ArrayList<>();
        PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(this);
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType childType = child.getNode().getElementType();
            TextRange relChildRange = child.getTextRangeInParent();
            if (PyTokenTypes.FSTRING_TEXT_TOKENS.contains(childType)) {
                result.addAll(decoder.decodeRange(relChildRange));
            }
            else if (childType == PyElementTypes.FSTRING_FRAGMENT) {
                // There shouldn't be any escaping inside interpolated parts
                result.add(Pair.create(relChildRange, child.getText()));
            }
        }
        return result;
    }

    @Override
    @RequiredReadAction
    default String getQuote() {
        PsiElement start = findChildByTypeNotNull(this, PyTokenTypes.FSTRING_START);
        return start.getText().substring(getPrefixLength());
    }

    @Override
    default boolean isTerminated() {
        return findChildByType(this, PyTokenTypes.FSTRING_END) != null;
    }

    @Override
    @RequiredReadAction
    default boolean isFormatted() {
        return StringUtil.containsIgnoreCase(getPrefix(), "f");
    }

    @Override
    @RequiredReadAction
    default boolean isTemplate() {
        return StringUtil.containsIgnoreCase(getPrefix(), "t");
    }
}
