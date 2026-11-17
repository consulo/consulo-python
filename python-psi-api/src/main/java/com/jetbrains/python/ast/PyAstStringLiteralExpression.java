/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.LiteralTextEscaper;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A string literal expression can consist of a number of implicitly concatenated string literals
 * each with its own set of quotes and prefixes. For instance, the following string literal expression 
 * in the right hand side of the assignment:
 * <p>
 * <code><pre>
 * s = """foo""" + rb'\bar' + f'{baz}'
 * </pre></code>
 * <p>
 * includes three distinct parts: a triple quoted string literal {@code """foo"""} without a prefix,
 * a "raw" bytes literal {@code rb'\bar'} using apostrophes as quotes and a formatted string literal
 * {@code f'{baz}'} containing the value of the expression {@code baz}.
 * <p>
 * PSI elements representing each of these parts can be acquired using {@link #getStringElements()} method.
 * Both plain and formatted literals forming a whole string literal expression implement {@link PyAstStringElement}
 * interface containing methods for retrieving general information about them such as quote types, prefixes,
 * decoded value, etc.
 *
 * @see <a href="https://docs.python.org/3/reference/lexical_analysis.html#string-literal-concatenation">
 *   https://docs.python.org/3/reference/lexical_analysis.html#string-literal-concatenation</a>
 * @see #getStringElements()
 * @see PyAstStringElement
 * @see PyAstPlainStringElement
 * @see PyAstFormattedStringElement
 */
public interface PyAstStringLiteralExpression extends PyAstLiteralExpression, StringLiteralExpression, PsiLanguageInjectionHost {
    @Override
    default boolean isValidHost() {
        return true;
    }

    @Override
    @RequiredReadAction
    default PsiLanguageInjectionHost updateText(String text) {
        return ElementManipulators.handleContentChange(this, text);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyStringLiteralExpression(this);
    }

    @RequiredReadAction
    default List<ASTNode> getStringNodes() {
        TokenSet stringNodeTypes = TokenSet.orSet(PyTokenTypes.STRING_NODES, TokenSet.create(PyElementTypes.FSTRING_NODE));
        return Arrays.asList(getNode().getChildren(stringNodeTypes));
    }

    /**
     * Returns a list of implicitly concatenated string elements composing this literal expression.
     */
    @RequiredReadAction
    default List<? extends PyAstStringElement> getStringElements() {
        return getStringNodes().stream()
            .map(ASTNode::getPsi)
            .filter(PyAstStringElement.class::isInstance)
            .map(PyAstStringElement.class::cast)
            .collect(Collectors.toList());
    }

    /**
     * Returns value ranges for all nodes that form this string literal expression <i>relative to its start offset</i>.
     * Such range doesn't include neither node's prefix like "ur", nor its quotes.
     * <p>
     * For example, for the next "glued" string literal:
     * <pre>{@code
     * u"\u0066\x6F\157" ur'' '''\t'''
     * }</pre>
     * <p>
     * this method returns:
     * <p>
     * <code><pre>
     * [
     *   [2,16),
     *   [21,21),
     *   [26,28),
     * ]
     * </code></pre>
     */
    @RequiredReadAction
    default List<TextRange> getStringValueTextRanges() {
        int elementStart = getTextRange().getStartOffset();
        return ContainerUtil.map(getStringElements(), node -> {
            int nodeRelativeOffset = node.getTextRange().getStartOffset() - elementStart;
            return node.getContentRange().shiftRight(nodeRelativeOffset);
        });
    }

    @Override
    @RequiredReadAction
    default String getStringValue() {
        StringBuilder out = new StringBuilder();
        for (Pair<TextRange, String> fragment : getDecodedFragments()) {
            out.append(fragment.getSecond());
        }
        return out.toString();
    }

    /**
     * Returns unescaped fragments of string's value together with their respective text ranges <i>relative to the element's start offset</i>.
     * For most escape sequences the decoded character is returned and the text range that spans the sequence itself.
     * Other "literal" fragments of the string are returned as is so that {@code pair.getFirst().length() == pair.getSecond().getLength()}.
     * <p>
     * For example, for the next "glued" string literal:
     * <p>
     * <pre>{@code
     * u"\u0066\x6F\157" '\bar' r'\baz'
     * }</pre>
     * <p>
     * this method returns:
     * <p>
     * <code><pre>
     * [
     *   ([2,8),"f"),
     *   ([8,12),"o"),
     *   ([12,16),"o"),
     *   ([16,16),""),
     *   ([19,21),"\b"),
     *   ([21,23),"ar"),
     *   ([27,29),"\\b"),
     *   ([29,31),"az"),
     * ]
     * </code></pre>
     */
    @RequiredReadAction
    default List<Pair<TextRange, String>> getDecodedFragments() {
        int elementStart = getTextRange().getStartOffset();
        return getStringElements().stream()
            .flatMap(node -> node.getDecodedFragments().stream()
                .map(pair -> {
                    int nodeRelativeOffset = node.getTextRange().getStartOffset() - elementStart;
                    return Pair.create(pair.getFirst().shiftRight(nodeRelativeOffset), pair.getSecond());
                }))
            .collect(Collectors.toList());
    }

    @Override
    @RequiredReadAction
    default TextRange getStringValueTextRange() {
        List<TextRange> allRanges = getStringValueTextRanges();
        if (allRanges.size() == 1) {
            return allRanges.get(0);
        }
        if (allRanges.size() > 1) {
            return allRanges.get(0).union(allRanges.get(allRanges.size() - 1));
        }
        return new TextRange(0, getTextLength());
    }

    @Override
    @RequiredReadAction
    default LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new LiteralTextEscaper<>(this) {
            @Override
            public boolean decode(TextRange rangeInsideHost, StringBuilder outChars) {
                for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
                    TextRange encodedTextRange = fragment.getFirst();
                    TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
                    if (intersection != null && !intersection.isEmpty()) {
                        String value = fragment.getSecond();
                        String intersectedValue;
                        if (value.codePointCount(0, value.length()) == 1 || value.length() == intersection.getLength()) {
                            intersectedValue = value;
                        }
                        else {
                            int start = Math.max(0, rangeInsideHost.getStartOffset() - encodedTextRange.getStartOffset());
                            int end = Math.min(value.length(), start + intersection.getLength());
                            intersectedValue = value.substring(start, end);
                        }
                        outChars.append(intersectedValue);
                    }
                }
                return true;
            }

            @Override
            public int getOffsetInHost(int offsetInDecoded, TextRange rangeInsideHost) {
                int offset = 0; // running offset in the decoded fragment
                int endOffset = -1;
                for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
                    TextRange encodedTextRange = fragment.getFirst();
                    TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
                    if (intersection != null && !intersection.isEmpty()) {
                        String value = fragment.getSecond();
                        int valueLength = value.length();
                        int intersectionLength = intersection.getLength();
                        if (valueLength == 0) {
                            return -1;
                        }
                        // A long unicode escape of form \U01234567 can be decoded into a surrogate pair
                        else if (value.codePointCount(0, valueLength) == 1) {
                            if (offset == offsetInDecoded) {
                                return intersection.getStartOffset();
                            }
                            offset += valueLength;
                        }
                        else {
                            // Literal fragment without escapes: it's safe to use intersection length instead of value length
                            if (offset + intersectionLength >= offsetInDecoded) {
                                int delta = offsetInDecoded - offset;
                                return intersection.getStartOffset() + delta;
                            }
                            offset += intersectionLength;
                        }
                        endOffset = intersection.getEndOffset();
                    }
                }
                // XXX: According to the real use of getOffsetInHost() it should return the correct host offset for the offset in decoded at the
                // end of the range inside host, not -1
                if (offset == offsetInDecoded) {
                    return endOffset;
                }
                return -1;
            }

            @Override
            public boolean isOneLine() {
                return true;
            }

            @Override
            public TextRange getRelevantTextRange() {
                return myHost.getStringValueTextRange();
            }
        };
    }

    /**
     * @return true if this element has single string node and its type is {@link com.jetbrains.python.PyTokenTypes#DOCSTRING}
     */
    @RequiredReadAction
    default boolean isDocString() {
        List<ASTNode> stringNodes = getStringNodes();
        return stringNodes.size() == 1 && stringNodes.get(0).getElementType() == PyTokenTypes.DOCSTRING;
    }
}
