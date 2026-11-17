// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.document.util.TextRange;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes Sphinx markup inside docstrings and line comments. Turns a Python-domain target into a
 * {@link PySphinxReference}, and reports the markup ranges that natural-language analysis must skip.
 * See the <a href="https://www.sphinx-doc.org/en/master/usage/domains/python.html">Python domain documentation</a>.
 */
public final class SphinxReferences {
    /**
     * Inline cross-referencing roles of the Python domain, with or without the explicit {@code py:} domain prefix.
     */
    private static final String ROLE_NAMES = "mod|func|class|meth|attr|data|const|exc|obj|type|deco";

    /**
     * Object-describing directives of the Python domain.
     */
    private static final String DIRECTIVE_NAMES =
        "module|currentmodule|function|decorator|decoratormethod|class|exception|attribute|data|method|staticmethod|classmethod|property|type";

    /**
     * Directives that carry prose as their argument, such as {@code .. note:: Close the socket.}. Only their marker is
     * markup. Every other directive, such as {@code .. code-block:: python} or {@code .. image:: logo.png}, carries a code or a
     * path argument, so the whole line is markup.
     */
    private static final String PROSE_DIRECTIVE_NAMES =
        "note|warning|tip|caution|important|attention|hint|danger|error|admonition|seealso|rubric|topic|sidebar|" +
            "deprecated|versionadded|versionchanged|versionremoved";

    private static final Pattern ROLE_PATTERN = Pattern.compile(":(?:py:)?(?:" + ROLE_NAMES + "):`([^`\\n]+)`");

    private static final Pattern DIRECTIVE_PATTERN =
        Pattern.compile("(?m)^[ \\t]*\\.\\.[ \\t]+(?:py:)?(?:" + DIRECTIVE_NAMES + ")::[ \\t]+(\\S+)");

    /**
     * Any reST inline role, not just a Python-domain one. It also covers {@code :ref:`label`}, {@code :doc:`/index`} and
     * {@code :envvar:`PATH`}. Used only for natural-language exclusion, because the target of such a role is never
     * prose. References and tag highlighting stay narrow ({@link #ROLE_PATTERN}) so only a Python object becomes a reference.
     */
    private static final String ANY_ROLE = ":[\\w.+-]+(?::[\\w.+-]+)*:`[^`\\n]+`";

    /**
     * The marker of a prose-carrying directive. The argument after it stays available to the grammar checker.
     */
    private static final String PROSE_DIRECTIVE_MARKER = "^[ \\t]*\\.\\.[ \\t]+(?:" + PROSE_DIRECTIVE_NAMES + ")::";

    /**
     * A whole directive line whose argument is code, a path or an option, such as {@code .. code-block:: python}.
     */
    private static final String OTHER_DIRECTIVE_LINE =
        "^[ \\t]*\\.\\.[ \\t]+(?!(?:" + PROSE_DIRECTIVE_NAMES + ")::)[\\w.+-]+(?::[\\w.+-]+)*::.*";

    private static final String DOCSTRING_MARKUP = ANY_ROLE + "|" + PROSE_DIRECTIVE_MARKER + "|" + OTHER_DIRECTIVE_LINE;

    /**
     * Matches the Sphinx markup of a docstring. Used to exclude the markup from natural-language analysis where the
     * coordinates are relative to the extracted text rather than to the PSI element.
     */
    public static final Pattern DOCSTRING_MARKUP_PATTERN = Pattern.compile(DOCSTRING_MARKUP, Pattern.MULTILINE);

    /**
     * Matches the Sphinx markup of a line comment, which is a role only. A directive pattern anchors on the start of a
     * line, and the text of a comment starts with the {@code #} marker, so a directive can never match there. Sphinx does
     * not read a {@code #} comment either, so a directive in one is not markup.
     */
    public static final Pattern COMMENT_MARKUP_PATTERN = Pattern.compile(ANY_ROLE);

    private static final Pattern DOCSTRING_MARKUP_REGEX = DOCSTRING_MARKUP_PATTERN;

    private static final Pattern COMMENT_MARKUP_REGEX = COMMENT_MARKUP_PATTERN;

    /**
     * Matches just the role marker (e.g. {@code :py:class:}) that precedes the backtick-quoted target.
     */
    private static final Pattern ROLE_TAG_PATTERN = Pattern.compile(":(?:py:)?(?:" + ROLE_NAMES + "):(?=`)");

    /**
     * Matches just the directive marker (e.g. {@code .. py:class::}), without the leading indentation.
     */
    private static final Pattern DIRECTIVE_TAG_PATTERN = Pattern.compile("(?m)^[ \\t]*(\\.\\.[ \\t]+(?:py:)?(?:" + DIRECTIVE_NAMES + ")::)");

    private SphinxReferences() {
    }

    /**
     * Returns the element-relative ranges of the Sphinx role and directive <em>markers</em> (e.g. {@code :py:class:},
     * {@code .. py:class::}), without the referenced target, so they can be highlighted like a docstring field tag
     * ({@code :param:}, {@code :return:}).
     */
    public static List<TextRange> findTagRanges(PyStringLiteralExpression host) {
        if (isInInjectedFragment(host)) {
            return List.of();
        }
        return mapValueRanges(host, SphinxReferences::tagRangesIn);
    }

    /**
     * Returns the comment-relative ranges of the Sphinx role markers. A comment holds no directive.
     */
    public static List<TextRange> findTagRanges(PsiComment comment) {
        if (isInInjectedFragment(comment)) {
            return List.of();
        }
        List<TextRange> result = new ArrayList<>();
        Matcher m = ROLE_TAG_PATTERN.matcher(comment.getText());
        while (m.find()) {
            result.add(toTextRange(m, 0));
        }
        return result;
    }

    private static List<TextRange> tagRangesIn(String text, int offset) {
        List<TextRange> result = new ArrayList<>();
        Matcher roleMatcher = ROLE_TAG_PATTERN.matcher(text);
        while (roleMatcher.find()) {
            result.add(toTextRange(roleMatcher, offset));
        }
        Matcher directiveMatcher = DIRECTIVE_TAG_PATTERN.matcher(text);
        while (directiveMatcher.find()) {
            if (directiveMatcher.start(1) < 0) {
                continue;
            }
            result.add(new TextRange(directiveMatcher.start(1) + offset, directiveMatcher.end(1) + offset));
        }
        return result;
    }

    /**
     * A target name resolved relative to the host text, paired with its start offset.
     */
    private record Target(String name, int start, boolean soft) {
    }

    /**
     * Builds references for every Sphinx Python-domain target found in the given docstring expression.
     */
    public static List<PsiReference> findReferences(PyStringLiteralExpression host) {
        if (isInInjectedFragment(host)) {
            return List.of();
        }
        return mapValueRanges(host, (text, offset) -> collectReferences(host, offset, text));
    }

    /**
     * Builds references for every Sphinx Python-domain target found in the given line comment.
     */
    public static List<PsiReference> findReferences(PsiComment comment) {
        if (isInInjectedFragment(comment)) {
            return List.of();
        }
        return collectReferences(comment, 0, comment.getText());
    }

    /**
     * Sphinx reads no markup in an injected fragment, such as the body of a {@code .. code-block:: python} directive, because
     * it renders the fragment as literal code. The host docstring skips the same range in {@link #mapValueRanges}. So a
     * docstring or a comment of the fragment gets no reference and no tag highlighting.
     */
    private static boolean isInInjectedFragment(PsiElement element) {
        return InjectedLanguageManager.getInstance(element.getProject()).isInjectedFragment(element.getContainingFile());
    }

    private static List<PsiReference> collectReferences(PsiElement host, int offset, String text) {
        List<Target> targets = new ArrayList<>();
        Matcher roleMatcher = ROLE_PATTERN.matcher(text);
        while (roleMatcher.find()) {
            if (roleMatcher.group(1) != null) {
                Target target = parseRoleTarget(roleMatcher.group(1), roleMatcher.start(1));
                if (target != null) {
                    targets.add(target);
                }
            }
        }
        // A directive signature gets a soft reference: navigate when possible, never warn. Sphinx qualifies the name by
        // the context, which this reference does not track. That context is the `.. py:currentmodule::` directive and an
        // enclosing directive, so `close` under `.. py:class:: Socket` means `Socket.close`. A signature can also
        // document an object that has no Python source, such as a member of a C extension.
        if (!(host instanceof PsiComment)) {
            Matcher directiveMatcher = DIRECTIVE_PATTERN.matcher(text);
            while (directiveMatcher.find()) {
                if (directiveMatcher.group(1) != null) {
                    Target target = parseSignatureTarget(directiveMatcher.group(1), directiveMatcher.start(1));
                    if (target != null) {
                        targets.add(target);
                    }
                }
            }
        }

        List<PsiReference> result = new ArrayList<>();
        for (Target target : targets) {
            result.addAll(buildComponentReferences(host, offset, target));
        }
        return result;
    }

    /**
     * Returns the element-relative ranges of the Sphinx markup so the spellchecker can skip both the role keyword and
     * the referenced names. The ranges are sorted and they do not overlap.
     */
    public static List<TextRange> findMarkupRanges(PyStringLiteralExpression host) {
        return mapValueRanges(host, (text, offset) -> {
            List<TextRange> ranges = new ArrayList<>();
            Matcher m = DOCSTRING_MARKUP_REGEX.matcher(text);
            while (m.find()) {
                ranges.add(toTextRange(m, offset));
            }
            return ranges;
        });
    }

    public static List<TextRange> findMarkupRanges(PsiComment comment) {
        List<TextRange> ranges = new ArrayList<>();
        Matcher m = COMMENT_MARKUP_REGEX.matcher(comment.getText());
        while (m.find()) {
            ranges.add(toTextRange(m, 0));
        }
        return ranges;
    }

    /**
     * Applies {@code mapper} to the raw text of every string element of {@code host}, and returns the concatenated result.
     * An offset in the result is relative to the start of {@code host}. A range that a language injection covers, such as
     * the body of a {@code .. code-block:: python} directive or a doctest line, is skipped: Sphinx reads no markup there.
     */
    private static <T> List<T> mapValueRanges(PyStringLiteralExpression host, BiFunction<String, Integer, List<T>> mapper) {
        List<TextRange> valueRanges = host.getStringValueTextRanges();
        if (valueRanges.isEmpty()) {
            return List.of();
        }
        String hostText = host.getText();
        List<TextRange> injected = injectedRanges(host);
        List<T> result = new ArrayList<>();
        for (TextRange valueRange : valueRanges) {
            if (injected.stream().anyMatch(it -> it.intersects(valueRange))) {
                // Split the value around the injected fragments and map each remaining piece on its own.
                for (TextRange piece : subtractRanges(valueRange, injected)) {
                    result.addAll(mapper.apply(piece.substring(hostText), piece.getStartOffset()));
                }
            }
            else {
                result.addAll(mapper.apply(valueRange.substring(hostText), valueRange.getStartOffset()));
            }
        }
        return result;
    }

    private static List<TextRange> injectedRanges(PsiElement host) {
        List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
        if (files == null) {
            return List.of();
        }
        List<TextRange> result = new ArrayList<>(files.size());
        for (Pair<PsiElement, TextRange> file : files) {
            result.add(file.getSecond());
        }
        return result;
    }

    private static List<TextRange> subtractRanges(TextRange range, List<TextRange> holes) {
        List<TextRange> overlaps = new ArrayList<>();
        for (TextRange hole : holes) {
            TextRange intersection = range.intersection(hole);
            if (intersection != null && !intersection.isEmpty()) {
                overlaps.add(intersection);
            }
        }
        overlaps.sort(Comparator.comparingInt(TextRange::getStartOffset));
        List<TextRange> result = new ArrayList<>();
        int current = range.getStartOffset();
        for (TextRange hole : overlaps) {
            if (hole.getStartOffset() > current) {
                result.add(new TextRange(current, hole.getStartOffset()));
            }
            current = Math.max(current, hole.getEndOffset());
        }
        if (current < range.getEndOffset()) {
            result.add(new TextRange(current, range.getEndOffset()));
        }
        return result;
    }

    private static TextRange toTextRange(Matcher matcher, int offset) {
        return new TextRange(matcher.start() + offset, matcher.end() + offset);
    }

    /**
     * Extracts the dotted target from the content of a role, honoring {@code ~}, {@code !}, {@code .} and the {@code text <target>} form.
     */
    private static @Nullable Target parseRoleTarget(String content, int contentStart) {
        String value = content;
        int start = contentStart;

        // Explicit title: `text <target>` -> reference only the target.
        int lt = value.lastIndexOf('<');
        if (lt >= 0 && value.endsWith(">")) {
            start += lt + 1;
            value = value.substring(lt + 1, value.length() - 1);
        }

        if (value.startsWith("!")) {
            return null;  // suppressed reference
        }

        boolean isSoft = false;
        if (value.startsWith("~")) {
            value = value.substring(1);
            start += 1;
        }
        if (value.startsWith(".")) {
            // Leading dot triggers Sphinx suffix lookup; resolution is best-effort, so do not warn.
            value = value.substring(1);
            start += 1;
            isSoft = true;
        }
        if (value.endsWith("()")) {
            value = value.substring(0, value.length() - 2);
        }
        if (value.isEmpty()) {
            return null;
        }
        return new Target(value, start, isSoft);
    }

    /**
     * Extracts the dotted name from a directive signature, dropping any parameter or type-parameter list.
     */
    private static @Nullable Target parseSignatureTarget(String signature, int signatureStart) {
        int end = -1;
        for (int i = 0; i < signature.length(); i++) {
            char c = signature.charAt(i);
            if (c == '(' || c == '[') {
                end = i;
                break;
            }
        }
        String name = end >= 0 ? signature.substring(0, end) : signature;
        if (name.isEmpty()) {
            return null;
        }
        return new Target(name, signatureStart, true);
    }

    private static List<PsiReference> buildComponentReferences(PsiElement host, int offset, Target target) {
        String[] parts = target.name().split("\\.", -1);
        List<String> names = new ArrayList<>(parts.length);
        List<PySphinxReference> references = new ArrayList<>(parts.length);
        int pos = target.start();
        for (String part : parts) {
            int start = pos;
            pos += part.length() + 1;  // skip the '.' separator
            if (part.isEmpty() || !PyNames.isIdentifier(part)) {
                return List.of();
            }
            names.add(part);
            TextRange range = new TextRange(start + offset, start + part.length() + offset);
            TextRange prefixRange = new TextRange(target.start() + offset, start + part.length() + offset);
            references.add(new PySphinxReference(host, range, prefixRange, QualifiedName.fromComponents(List.copyOf(names)), target.soft()));
        }
        if (references.size() > 1) {
            PySphinxReference head = references.get(0);
            references.forEach(it -> it.head = head);
        }
        return new ArrayList<>(references);
    }
}
