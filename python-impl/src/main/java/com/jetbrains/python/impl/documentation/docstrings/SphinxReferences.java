// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import java.util.regex.Pattern;

/**
 * Recognizes Sphinx markup inside docstrings and line comments. Turns a Python-domain target into a
 * {@code PySphinxReference}, and reports the markup ranges that natural-language analysis must skip.
 * See the <a href="https://www.sphinx-doc.org/en/master/usage/domains/python.html">Python domain documentation</a>.
 * <p>
 */
public final class SphinxReferences {
    /**
     * Directives that carry prose as their argument, such as {@code .. note:: Close the socket.}. Only their marker is
     * markup. Every other directive, such as {@code .. code-block:: python} or {@code .. image:: logo.png}, carries a code or a
     * path argument, so the whole line is markup.
     */
    private static final String PROSE_DIRECTIVE_NAMES =
        "note|warning|tip|caution|important|attention|hint|danger|error|admonition|seealso|rubric|topic|sidebar|" +
            "deprecated|versionadded|versionchanged|versionremoved";

    /**
     * Any reST inline role, not just a Python-domain one. It also covers {@code :ref:`label`}, {@code :doc:`/index`} and
     * {@code :envvar:`PATH`}. Used only for natural-language exclusion, because the target of such a role is never
     * prose. References and tag highlighting stay narrow ({@code ROLE_PATTERN}) so only a Python object becomes a reference.
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

    private SphinxReferences() {
    }
}
