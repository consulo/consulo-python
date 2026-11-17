package com.jetbrains.python.ast;

import com.jetbrains.python.psi.PyStringLiteralUtil;
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
 * <li>{@code '\u0041 \x41 \N{LATIN CAPITAL LETTER A}'}</li>
 * </ul>
 */
public interface PyAstPlainStringElement extends PyAstStringElement {
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
        return text.length() >= getPrefixLength() +  quote.length() * 2 && text.endsWith(quote);
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
