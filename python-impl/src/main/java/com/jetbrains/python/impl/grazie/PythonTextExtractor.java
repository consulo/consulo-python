// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
package com.jetbrains.python.impl.grazie;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.documentation.docstrings.SphinxDocString;
import com.jetbrains.python.impl.documentation.docstrings.SphinxReferences;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralDecoder;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.inspections.PyStringFormatParser.ConstantChunk;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.PsiCoreCommentImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.spellcheker.text.TextContent;
import consulo.language.spellcheker.text.TextContent.TextDomain;
import consulo.language.spellcheker.text.TextContentBuilder;
import consulo.language.spellcheker.text.TextExtractor;
import consulo.language.spellcheker.text.util.PsiUtils;
import consulo.language.spellcheker.text.util.Text;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ExtensionImpl
public class PythonTextExtractor extends TextExtractor {
    private static final String KNOWN_DOCSTRING_TAGS_PATTERN =
        Arrays.stream(SphinxDocString.ALL_TAGS).map(Pattern::quote).collect(Collectors.joining("|", "(", ")"));
    private static final Pattern DOCSTRING_DIRECTIVE_PATTERN =
        Pattern.compile("^" + KNOWN_DOCSTRING_TAGS_PATTERN + "[^\n:]*: *", Pattern.MULTILINE);

    @Override
    @RequiredReadAction
    protected List<TextContent> buildTextContents(PsiElement root, Set<TextDomain> allowedDomains) {
        if (root instanceof PyStringLiteralExpression stringLiteral) {
            List<TextContent> texts = new ArrayList<>();
            PsiElement parent = root.getParent();
            if (parent instanceof PyBinaryExpression binaryExpression
                && root == binaryExpression.getLeftExpression()
                && binaryExpression.getOperator() == PyTokenTypes.PERC) {
                for (PyStringFormatParser.FormatStringChunk chunk : PyStringFormatParser.parsePercentFormat(stringLiteral.getStringValue())) {
                    if (chunk instanceof ConstantChunk) {
                        int startIndex = stringLiteral.valueOffsetToTextOffset(chunk.getStartIndex());
                        int endIndex = stringLiteral.valueOffsetToTextOffset(chunk.getEndIndex());
                        ContainerUtil.addIfNotNull(texts,
                            TextContentBuilder.FromPsi.build(root, TextDomain.LITERALS, new TextRange(startIndex, endIndex)));
                    }
                }
                return texts;
            }

            for (ASTNode node : stringLiteral.getStringNodes()) {
                PsiElement element = node.getPsi();
                String prefix = PyStringLiteralUtil.getPrefix(node.getText());
                List<TextRange> ranges = List.of(PyStringLiteralExpressionImpl.getNodeTextRange(node.getText()));
                PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(node);
                boolean containsEscapes = element.textContains('\\');
                for (TextRange range : ranges) {
                    List<TextRange> escapeAwareRanges = PyStringLiteralUtil.isRawPrefix(prefix) || !containsEscapes
                        ? List.of(range)
                        : ContainerUtil.map(decoder.decodeRange(range), it -> it.getFirst());
                    for (TextRange escapeAwareRange : escapeAwareRanges) {
                        TextDomain domain = getDomain(element);
                        TextContent text = TextContentBuilder.FromPsi
                            .removingIndents(" \t")
                            .removingLineSuffixes(" \t")
                            .build(element, domain, escapeAwareRange);
                        if (text != null) {
                            if (domain == TextDomain.DOCUMENTATION) {
                                ContainerUtil.addIfNotNull(texts, excludeDocstringMarkup(text));
                            }
                            else {
                                texts.add(text);
                            }
                        }
                    }
                }
            }
            return texts;
        }

        if (root instanceof PsiCoreCommentImpl && allowedDomains.contains(TextDomain.COMMENTS)) {
            List<PsiElement> siblings = PsiUtils.getNotSoDistantSimilarSiblings(root, it -> it instanceof PsiCoreCommentImpl);
            TextContent text = TextContent.joinWithWhitespace(
                '\n',
                ContainerUtil.mapNotNull(siblings, it -> TextContent.builder().build(it, TextDomain.COMMENTS))
            );
            if (text == null) {
                return Collections.emptyList();
            }
            // A Sphinx role in a comment is markup, not prose.
            List<TextRange> ranges = Text.allOccurrences(SphinxReferences.COMMENT_MARKUP_PATTERN, text);
            TextContent cleaned = text.excludeRanges(ContainerUtil.map(merged(ranges), TextContent.Exclusion::exclude));
            return cleaned == null ? Collections.emptyList() : List.of(cleaned);
        }

        return Collections.emptyList();
    }

    private static TextDomain getDomain(PsiElement element) {
        IElementType elementType = PsiUtilCore.getElementType(element);
        return elementType == PyTokenTypes.DOCSTRING ? TextDomain.DOCUMENTATION : TextDomain.LITERALS;
    }

    private static @Nullable TextContent excludeDocstringMarkup(TextContent content) {
        // Exclude the docstring field tags (:param:, :rtype:, ...) and the Sphinx markup, so the grammar checker does not
        // read the raw markup as prose.
        // The two patterns can overlap. The tag `:type` is also the name of a Sphinx role, and the tag pattern runs
        // forward to the first colon, which can be the colon of a role. `excludeRanges` rejects an overlap, so merge first.
        List<TextRange> ranges = new ArrayList<>(Text.allOccurrences(DOCSTRING_DIRECTIVE_PATTERN, content));
        ranges.addAll(Text.allOccurrences(SphinxReferences.DOCSTRING_MARKUP_PATTERN, content));
        return content.excludeRanges(ContainerUtil.map(merged(ranges), TextContent.Exclusion::exclude));
    }

    /**
     * Sorts the ranges and joins every pair that overlaps or touches, because {@link TextContent#excludeRanges} rejects both.
     */
    private static List<TextRange> merged(List<TextRange> ranges) {
        if (ranges.size() < 2) {
            return ranges;
        }
        List<TextRange> result = new ArrayList<>(ranges.size());
        List<TextRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(TextRange::getStartOffset));
        for (TextRange range : sorted) {
            TextRange last = result.isEmpty() ? null : result.get(result.size() - 1);
            if (last != null && range.intersects(last)) {
                result.set(result.size() - 1, range.union(last));
            }
            else {
                result.add(range);
            }
        }
        return result;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
