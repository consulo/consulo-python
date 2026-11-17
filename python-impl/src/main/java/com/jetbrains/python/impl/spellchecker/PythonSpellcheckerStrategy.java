// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.spellchecker;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.SphinxReferences;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.util.registry.Registry;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.spellcheker.SpellcheckingStrategy;
import consulo.language.spellcheker.tokenizer.TokenConsumer;
import consulo.language.spellcheker.tokenizer.Tokenizer;
import consulo.language.spellcheker.tokenizer.splitter.CommentTokenSplitter;
import consulo.language.spellcheker.tokenizer.splitter.PlainTextTokenSplitter;
import consulo.language.spellcheker.tokenizer.splitter.TokenSplitter;
import consulo.util.collection.ContainerUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@ExtensionImpl
public final class PythonSpellcheckerStrategy extends SpellcheckingStrategy implements DumbAware {
    private static class StringLiteralTokenizer extends Tokenizer<PyStringLiteralExpression> {
        @Override
        public void tokenize(PyStringLiteralExpression element, TokenConsumer consumer) {
            TokenSplitter splitter = PlainTextTokenSplitter.getInstance();
            // Sphinx cross-reference roles/directives in docstrings hold code identifiers, not prose: skip them.
            List<TextRange> markupRanges = DocStringUtil.getParentDefinitionDocString(element) == element
                ? SphinxReferences.findMarkupRanges(element)
                : Collections.emptyList();
            for (PyStringElement stringElement : element.getStringElements()) {
                int stringElementShift = stringElement.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
                List<TextRange> literalPartRanges;
                if (stringElement.isFormatted()) {
                    literalPartRanges = ((PyFormattedStringElement) stringElement).getLiteralPartRanges();
                }
                else {
                    literalPartRanges = Collections.singletonList(stringElement.getContentRange());
                }
                PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(stringElement);
                boolean containsEscapes = stringElement.textContains('\\');
                for (TextRange literalPartRange : literalPartRanges) {
                    List<TextRange> escapeAwareRanges;
                    if (stringElement.isRaw() || !containsEscapes) {
                        escapeAwareRanges = Collections.singletonList(literalPartRange);
                    }
                    else {
                        escapeAwareRanges = ContainerUtil.map(decoder.decodeRange(literalPartRange), x -> x.getFirst());
                    }
                    for (TextRange escapeAwareRange : escapeAwareRanges) {
                        for (TextRange range : excludeMarkup(escapeAwareRange, markupRanges, stringElementShift)) {
                            String valueText = range.substring(stringElement.getText());
                            consumer.consumeToken(stringElement, valueText, false, range.getStartOffset(), TextRange.allOf(valueText), splitter);
                        }
                    }
                }
            }
        }
    }

    /**
     * Tokenizes a line comment with {@link CommentTokenSplitter}, skipping any Sphinx role/directive markup so referenced
     * identifiers (e.g. {@code # see :py:func:`socket.getdefaulttimeout`}) are not reported as typos.
     */
    private static class SphinxAwareCommentTokenizer extends Tokenizer<PsiComment> {
        @Override
        public void tokenize(PsiComment element, TokenConsumer consumer) {
            // doccomment chameleon expands as PsiComment inside PsiComment, avoid duplication (see platform CommentTokenizer)
            if (element.getParent() instanceof PsiComment) {
                return;
            }
            TokenSplitter splitter = CommentTokenSplitter.getInstance();
            String text = element.getText();
            List<TextRange> markupRanges = SphinxReferences.findMarkupRanges(element);
            for (TextRange range : excludeMarkup(new TextRange(0, text.length()), markupRanges, 0)) {
                String valueText = range.substring(text);
                consumer.consumeToken(element, valueText, false, range.getStartOffset(), TextRange.allOf(valueText), splitter);
            }
        }
    }

    /**
     * Splits {@code range} (in element/string-element coordinates) into the sub-ranges that remain after removing the
     * Sphinx markup ranges. {@code markupRangesInHost} are relative to the host element; {@code shift} is the offset of
     * {@code range}'s coordinate space inside the host (the offset of a string sub-element, or 0 for a comment).
     * <p>
     * The intersection runs in host coordinates. A markup range that belongs to another string sub-element would give a
     * negative offset in {@code range}'s space, and {@link TextRange} rejects that.
     */
    private static List<TextRange> excludeMarkup(TextRange range, List<TextRange> markupRangesInHost, int shift) {
        if (markupRangesInHost.isEmpty()) {
            return Collections.singletonList(range);
        }
        TextRange rangeInHost = range.shiftRight(shift);
        List<TextRange> excludes = new ArrayList<>();
        for (TextRange markup : markupRangesInHost) {
            TextRange overlap = rangeInHost.intersection(markup);
            if (overlap != null && !overlap.isEmpty()) {
                excludes.add(overlap.shiftLeft(shift));
            }
        }
        if (excludes.isEmpty()) {
            return Collections.singletonList(range);
        }
        excludes.sort(Comparator.comparingInt(TextRange::getStartOffset));
        List<TextRange> result = new ArrayList<>();
        int cur = range.getStartOffset();
        for (TextRange exclude : excludes) {
            if (exclude.getStartOffset() > cur) {
                result.add(new TextRange(cur, exclude.getStartOffset()));
            }
            cur = Math.max(cur, exclude.getEndOffset());
        }
        if (cur < range.getEndOffset()) {
            result.add(new TextRange(cur, range.getEndOffset()));
        }
        return result;
    }

    private static class FormatStringTokenizer extends Tokenizer<PyStringLiteralExpression> {
        @Override
        public void tokenize(PyStringLiteralExpression element, TokenConsumer consumer) {
            String stringValue = element.getStringValue();
            List<PyStringFormatParser.FormatStringChunk> chunks = PyStringFormatParser.parsePercentFormat(stringValue);
            TokenSplitter splitter = PlainTextTokenSplitter.getInstance();
            for (PyStringFormatParser.FormatStringChunk chunk : chunks) {
                if (chunk instanceof PyStringFormatParser.ConstantChunk) {
                    int startIndex = element.valueOffsetToTextOffset(chunk.getStartIndex());
                    int endIndex = element.valueOffsetToTextOffset(chunk.getEndIndex());
                    String text = element.getText().substring(startIndex, endIndex);
                    consumer.consumeToken(element, text, false, startIndex, TextRange.allOf(text), splitter);
                }
            }
        }
    }

    // TODO consulo: SpellcheckingStrategy#useTextLevelSpellchecking() missing, kept as a plain method
    public boolean useTextLevelSpellchecking() {
        return Registry.is("spellchecker.grazie.enabled", false);
    }

    private final StringLiteralTokenizer myStringLiteralTokenizer = new StringLiteralTokenizer();
    private final FormatStringTokenizer myFormatStringTokenizer = new FormatStringTokenizer();
    private final SphinxAwareCommentTokenizer mySphinxCommentTokenizer = new SphinxAwareCommentTokenizer();

    @Override
    public Tokenizer getTokenizer(PsiElement element) {
        if (element instanceof PyStringLiteralExpression && !useTextLevelSpellchecking()) {
            InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(element.getProject());
            if (element.getTextLength() >= 2 && injectionManager.getInjectedPsiFiles(element) != null) {
                return EMPTY_TOKENIZER;
            }
            PsiElement parent = element.getParent();
            if (parent instanceof PyBinaryExpression binaryExpression) {
                if (element == binaryExpression.getLeftExpression() && binaryExpression.getOperator() == PyTokenTypes.PERC) {
                    return myFormatStringTokenizer;
                }
            }
            return myStringLiteralTokenizer;
        }
        if (element instanceof PsiComment && !useTextLevelSpellchecking()) {
            // Reuse the platform's suppression/shebang handling, then exclude Sphinx markup from the remaining text.
            return super.getTokenizer(element) == EMPTY_TOKENIZER ? EMPTY_TOKENIZER : mySphinxCommentTokenizer;
        }
        return super.getTokenizer(element);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
