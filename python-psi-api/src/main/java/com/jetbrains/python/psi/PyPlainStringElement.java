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

import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.util.lang.Pair;

import java.util.List;

/**
 * Represents a string literal which content in constant (without any interpolation taking place).
 * Namely, these are all kinds of string literals in Python except for f-strings and they map directly
 * to underlying tokens. The following are all examples of such elements:
 *
 * <ul>
 * <li>{@code r'foo\42'}</li>
 * <li><pre><code>
 * b"""\
 * multi
 * line
 * bytes"""</code><pre/></li>
 * <li>{@code 'A \x41 \N{LATIN CAPITAL LETTER A}'}</li>
 * </ul>
 */
// TODO consulo: JB extends com.jetbrains.python.ast.PyAstPlainStringElement (python-ast layer not ported),
//  its default methods are placed here
public interface PyPlainStringElement extends PyStringElement {
    @Override
    @RequiredReadAction
    default List<Pair<TextRange, String>> getDecodedFragments() {
        return new PyStringLiteralDecoder(this).decodeContent();
    }

    @Override
    @RequiredReadAction
    default TextRange getContentRange() {
        return PyStringLiteralUtil.getContentRange(getText());
    }

    @Override
    @RequiredReadAction
    default String getQuote() {
        return getText().substring(getPrefixLength(), getContentRange().getStartOffset());
    }

    @Override
    @RequiredReadAction
    default boolean isTerminated() {
        String text = getText();
        String quote = getQuote();
        return text.length() >= getPrefixLength() + quote.length() * 2 && text.endsWith(quote);
    }

    @Override
    default boolean isFormatted() {
        return false;
    }

    @Override
    default boolean isTemplate() {
        return false;
    }
}
