// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and matching for the two inline suppression directives PyCharm understands:
 * <ul>
 *  <li>{@code # type: ignore[<code>, ...]} — the PEP 484 comment; a bare code may name a foreign checker's code
 *    (e.g. mypy's {@code attr-defined});</li>
 *  <li>{@code # pycharm: ignore[<code>, ...]} — PyCharm's own directive (PY-90627); every bare code is implicitly a
 *    PyCharm code.</li>
 * </ul>
 * A {@code <code>} may carry an explicit {@code pycharm:} namespace prefix. This object stays purely syntactic: it does
 * not know which codes name real inspections. Semantic resolution (known suppress ids, kebab aliases,
 * granular type-checker codes) lives with the callers — {@code com.jetbrains.python.inspections.typeignore}
 * on the impl side for whole-inspection suppression, and {@link PyTypeCheckerProblemReporter} for granular codes.
 */
public final class PyIgnoreCommentUtil {
    private PyIgnoreCommentUtil() {
    }

    private static final String PYCHARM_NAMESPACE = "pycharm";

    /**
     * {@code # pycharm: ignore[...]}, mirroring {@link PyTypingTypeProvider#TYPE_IGNORE_PATTERN}; group(1) = the bracketed codes incl. brackets.
     */
    public static final Pattern PYCHARM_IGNORE_PATTERN =
        Pattern.compile("#\\s*pycharm:\\s*ignore\\s*(\\[[^]#]*])?($|(\\s.*))", Pattern.CASE_INSENSITIVE);

    /**
     * An ignore comment of either directive whose code list is still open, as in {@code # type: ignore[attr-defined, un}.
     * group(1) = the directive keyword, group(2) = the codes after the bracket. Completion matches it against the
     * comment text before the caret.
     */
    public static final Pattern UNCLOSED_CODES_PATTERN =
        Pattern.compile("#\\s*(type|pycharm):\\s*ignore\\s*\\[([^]#]*)", Pattern.CASE_INSENSITIVE);

    public enum Directive {
        TYPE, PYCHARM
    }

    /**
     * A parsed ignore comment. {@code rawCodes} is empty for a bare comment or empty brackets.
     */
    public record ParsedIgnore(Directive directive, Set<String> rawCodes) {
    }

    /**
     * A single bracket code after namespace canonicalization. {@code pycharmNamespaced} is {@code true} when the code
     * carried an explicit {@code pycharm:} prefix or came from a {@code # pycharm: ignore} directive.
     */
    public record CodeRef(String name, boolean pycharmNamespaced) {
    }

    /**
     * A raw code of an ignore comment and its range in the comment text.
     */
    public record CodeOccurrence(String rawCode, TextRange range) {
    }

    /**
     * The codes of an ignore comment in source order. {@code bracketContent} is the range between the brackets in the
     * comment text. It is {@code null} when the comment has no brackets.
     */
    public record IgnoreCodes(Directive directive, List<CodeOccurrence> occurrences, @Nullable TextRange bracketContent) {
    }

    /**
     * Parses {@code comment} as one of the two directives, or returns {@code null} if it is neither.
     */
    public static @Nullable ParsedIgnore parse(PsiComment comment) {
        IgnoreCodes codes = parseCodes(comment);
        if (codes == null) {
            return null;
        }
        Set<String> rawCodes = new LinkedHashSet<>();
        for (CodeOccurrence occurrence : codes.occurrences()) {
            rawCodes.add(occurrence.rawCode());
        }
        return new ParsedIgnore(codes.directive(), rawCodes);
    }

    /**
     * Parses {@code comment} like {@link #parse}, and keeps the range of each code.
     */
    public static @Nullable IgnoreCodes parseCodes(PsiComment comment) {
        String text = comment.getText();
        if (text == null) {
            return null;
        }
        IgnoreCodes codes = parseCodesWith(text, PyTypingTypeProvider.TYPE_IGNORE_PATTERN, Directive.TYPE);
        return codes != null ? codes : parseCodesWith(text, PYCHARM_IGNORE_PATTERN, Directive.PYCHARM);
    }

    private static @Nullable IgnoreCodes parseCodesWith(String text, Pattern pattern, Directive directive) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        if (matcher.group(1) == null) {
            return new IgnoreCodes(directive, List.of(), null);
        }
        TextRange bracketContent = new TextRange(matcher.start(1) + 1, matcher.end(1) - 1);
        List<CodeOccurrence> occurrences = new ArrayList<>();
        int partStart = bracketContent.getStartOffset();
        while (partStart <= bracketContent.getEndOffset()) {
            int comma = text.indexOf(',', partStart);
            int partEnd = comma < 0 || comma > bracketContent.getEndOffset() ? bracketContent.getEndOffset() : comma;
            String part = text.substring(partStart, partEnd);
            String code = part.trim();
            if (!code.isEmpty()) {
                int codeStart = partStart + part.indexOf(code);
                occurrences.add(new CodeOccurrence(code, new TextRange(codeStart, codeStart + code.length())));
            }
            partStart = partEnd + 1;
        }
        return new IgnoreCodes(directive, occurrences, bracketContent);
    }

    /**
     * Canonicalizes a single {@code rawCode} of a {@code directive}. Strips a {@code pycharm:} prefix; returns {@code null} when the
     * code belongs to a different, foreign namespace (e.g. {@code mypy:foo}).
     */
    public static @Nullable CodeRef codeRef(Directive directive, String rawCode) {
        int colon = rawCode.indexOf(':');
        if (colon < 0) {
            return new CodeRef(rawCode, directive == Directive.PYCHARM);
        }
        if (!rawCode.substring(0, colon).trim().equalsIgnoreCase(PYCHARM_NAMESPACE)) {
            return null;
        }
        String name = rawCode.substring(colon + 1).trim();
        return name.isEmpty() ? null : new CodeRef(name, true);
    }

    /**
     * All canonical {@link CodeRef}s listed by {@code parsed}, dropping foreign-namespaced codes.
     */
    public static List<CodeRef> codeRefs(ParsedIgnore parsed) {
        List<CodeRef> result = new ArrayList<>();
        for (String rawCode : parsed.rawCodes()) {
            CodeRef ref = codeRef(parsed.directive(), rawCode);
            if (ref != null) {
                result.add(ref);
            }
        }
        return result;
    }

    /**
     * {@code true} when a same-line trailing ignore comment for {@code element}, or a leading file-level ignore comment,
     * explicitly lists {@code codeName} (in any non-foreign namespace). Used by {@link PyTypeCheckerProblemReporter} so a
     * granular type-checker code can be silenced with {@code # type: ignore[<code>]} / {@code # pycharm: ignore[<code>]}
     * exactly like {@code # noinspection <code>}.
     */
    public static boolean isExplicitlyIgnored(PsiElement element, String codeName) {
        PsiComment sameLine = PyPsiUtils.findSameLineComment(element);
        if (sameLine != null && namesCode(sameLine, codeName)) {
            return true;
        }
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return false;
        }
        PsiElement node = file.getFirstChild();
        while (node instanceof PsiComment || node instanceof PsiWhiteSpace) {
            if (node instanceof PsiComment comment && namesCode(comment, codeName)) {
                return true;
            }
            node = node.getNextSibling();
        }
        return false;
    }

    private static boolean namesCode(PsiComment comment, String codeName) {
        ParsedIgnore parsed = parse(comment);
        if (parsed == null) {
            return false;
        }
        for (CodeRef ref : codeRefs(parsed)) {
            if (ref.name().equals(codeName)) {
                return true;
            }
        }
        return false;
    }
}
