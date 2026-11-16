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

import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import java.util.List;

/**
 * A common interface containing utility methods shared among both plain string literals
 * (i.e. those that don't have "f" prefix) and formatted literals (f-strings).
 *
 * @see PyPlainStringElement
 * @see PyFormattedStringElement
 */
// TODO consulo: JB extends com.jetbrains.python.ast.PyAstStringElement (python-ast layer not ported),
//  its methods are placed here
public interface PyStringElement extends PsiElement {

    /**
     * Returns string prefix, e.g. "UR", "b", "f", etc.
     *
     * @see #getPrefixLength()
     */
    @RequiredReadAction
    default String getPrefix() {
        return PyStringLiteralCoreUtil.getPrefix(getText());
    }

    /**
     * Returns the length of the prefix.
     *
     * @see #getPrefix()
     */
    @RequiredReadAction
    default int getPrefixLength() {
        return PyStringLiteralUtil.getPrefixLength(getText());
    }

    /**
     * Returns <em>relative</em> range of this element actual content that contributes to {@link StringLiteralExpression#getStringValue()},
     * i.e. excluding prefix and quotes.
     *
     * @see #getContent()
     */
    @RequiredReadAction
    TextRange getContentRange();

    /**
     * Returns the content of the string node between quotes
     *
     * @see #getContentRange()
     */
    @RequiredReadAction
    default String getContent() {
        return getContentRange().substring(getText());
    }

    /**
     * Has the same meaning as {@link PyStringLiteralExpression#getDecodedFragments()} but applied to individual string literals
     * composing it.
     *
     * @see PyStringLiteralExpression#getDecodedFragments()
     */
    @RequiredReadAction
    List<Pair<TextRange, String>> getDecodedFragments();

    /**
     * Returns starting quotes of the string literal, regardless of whether it's properly terminated or not.
     *
     * @see #isTerminated()
     */
    @RequiredReadAction
    String getQuote();

    /**
     * Returns whether this string literal is enclosed in triple quotes and thus can contain unescaped line breaks.
     */
    @RequiredReadAction
    default boolean isTripleQuoted() {
        return getQuote().length() == 3;
    }

    /**
     * Returns whether this string literal is properly terminated with the same type of quotes it begins with.
     */
    @RequiredReadAction
    boolean isTerminated();

    /**
     * Returns whether this string literal contains "u" or "U" prefix.
     * <p>
     * It's unrelated to which type the corresponding string literal expression actually has at runtime.
     */
    @RequiredReadAction
    default boolean isUnicode() {
        return StringUtil.containsIgnoreCase(getPrefix(), "u");
    }

    /**
     * Returns whether this string literal contains "r" or "R" prefix.
     */
    @RequiredReadAction
    default boolean isRaw() {
        return StringUtil.containsIgnoreCase(getPrefix(), "r");
    }

    /**
     * Returns whether this string literal contains "b" or "B" prefix.
     */
    @RequiredReadAction
    default boolean isBytes() {
        return StringUtil.containsIgnoreCase(getPrefix(), "b");
    }

    /**
     * Returns whether this string literal contains "f" or "F" prefix.
     * <p>
     * It can be used as a counterpart for {@code this isinstance PyFormattedStringElement}, since only this implementation
     * is allowed to have such prefix.
     */
    @RequiredReadAction
    boolean isFormatted();

    /**
     * Returns whether this string literal contains "t" or "T" prefix.
     * <p>
     * Template strings (t-strings) are a new feature in Python 3.14 for handling
     * text templates with embedded expressions.
     */
    @RequiredReadAction
    boolean isTemplate();
}
