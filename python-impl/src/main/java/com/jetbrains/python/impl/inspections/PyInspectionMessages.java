// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.documentation.PyDocumentationLink;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.HtmlChunk;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeKey;
import consulo.localize.LocalizeManager;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import consulo.util.lang.xml.XmlStringUtil;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Helpers for building PY inspection messages whose bundle templates mark code-like spans with backticks.
 * <p>
 * A bundle template such as
 * <pre>
 * INSP.foo.bad.type=Expected type `{0}`, got `{1}` instead
 * INSP.bar.references=References must always resolve to `int`
 * </pre>
 * yields two formatted strings:
 * <ul>
 *  <li>a plain-text description used in the Problems view (e.g. `Expected type 'int', got 'str' instead`),</li>
 *  <li>an HTML tooltip used for the editor hover (e.g. `Expected type &lt;code&gt;int&lt;/code&gt;, got &lt;code&gt;str&lt;/code&gt; instead`).</li>
 * </ul>
 * <p>
 * Only backticks present in the template are translated. Backticks that appear in the params values are
 * substituted verbatim and never converted, so type names that happen to contain backticks survive into
 * both the description and the tooltip untouched.
 * <p>
 * Write a doubled {@code ``} in the template to emit a single literal backtick (mirroring MessageFormat's
 * {@code ''}-means-{@code '} rule); it is not treated as a code-span delimiter.
 * <p>
 * Templates without any backticks fall back to a plain formatted message identical to
 * AbstractBundle.getMessage; the tooltip is the same plain text as the description.
 */
public final class PyInspectionMessages {
    private PyInspectionMessages() {
    }

    /**
     * A localized inspection message paired with its editor-hover tooltip.
     */
    public record ProblemMessage(String description, String tooltip) {
    }

    /**
     * The {@code message}'s rich tooltip as a raw {@link HtmlChunk} (its outer {@code <html>} wrapper stripped), suitable for use as
     * a diff headline that must keep its embedded {@code <code>} spans and {@code #element/} links rather than being escaped.
     */
    public static HtmlChunk tooltipHeadline(ProblemMessage message) {
        return HtmlChunk.raw(removeSurrounding(message.tooltip(), "<html>", "</html>"));
    }

    /**
     * A parameter value that carries its own description form and tooltip form. Use this when the value is
     * a joined or pre-formatted span of code-like names — the bundle template can only convert its own
     * backticks, not characters inside dynamic inputs, so the caller supplies both forms here and the
     * helper substitutes the correct one into each template.
     * <p>
     * {@code tooltip} is expected to be HTML; it is inserted into the tooltip template as-is (no further
     * escaping). {@code description} is plain text used in the Problems view.
     */
    public record CodifiedParam(String description, String tooltip) {
        /**
         * Joins {@code names} as {@code 'a', 'b', 'c'} for the description and
         * {@code <code>a</code>, <code>b</code>, <code>c</code>} for the tooltip, separated by {@code ", "}.
         */
        public static CodifiedParam joinNames(Iterable<String> names) {
            return new CodifiedParam(
                StreamSupport.stream(names.spliterator(), false).map(it -> "'" + it + "'").collect(Collectors.joining(", ")),
                StreamSupport.stream(names.spliterator(), false)
                    .map(it -> "<code>" + XmlStringUtil.escapeString(it) + "</code>")
                    .collect(Collectors.joining(", "))
            );
        }

        public static CodifiedParam ofType(@Nullable PyType type, PsiElement anchor, TypeEvalContext context) {
            return ofType(type, anchor, context, false);
        }

        /**
         * Renders {@code type} as a code-like span: the description is the plain type name shown in the Problems view
         * (identical to {@link PythonDocumentationProvider#getTypeName}), and the tooltip is the same type rendered as
         * HTML with highlighted, clickable class links, exactly as Quick Documentation shows it.
         * <p>
         * Set {@code verbose} to mirror {@link PythonDocumentationProvider#getVerboseTypeName} (e.g. inline TypeVar bounds);
         * use it for the <i>expected</i> side of a type-mismatch message.
         * <p>
         * {@code anchor} is the element the type belongs to; it is used to resolve class names to their declarations.
         */
        public static CodifiedParam ofType(@Nullable PyType type, PsiElement anchor, TypeEvalContext context, boolean verbose) {
            return new CodifiedParam(
                verbose ? PythonDocumentationProvider.getVerboseTypeName(type, context)
                    : PythonDocumentationProvider.getTypeName(type, context),
                verbose ? PythonDocumentationProvider.getVerboseTypeNameWithLinks(type, context, anchor)
                    : PythonDocumentationProvider.getTypeNameWithLinks(type, context, anchor)
            );
        }

        public static CodifiedParam ofReference(PyQualifiedNameOwner target) {
            return ofReference(target, StringUtil.notNullize(target.getName()));
        }

        /**
         * Renders a named symbol ({@code target}) as a code-like span: the description is {@code text} (plain, shown in the
         * Problems view), and the tooltip is {@code text} wrapped in a navigable {@code #element/<fqn>} link pointing at
         * {@code target} — so e.g. a class or method name in an inspection message becomes clickable on hover.
         * <p>
         * {@code text} defaults to the symbol's own name; pass it explicitly when the message shows a different form,
         * such as a qualified {@code B.f()} signature.
         */
        public static CodifiedParam ofReference(PyQualifiedNameOwner target, String text) {
            return new CodifiedParam(text, PyDocumentationLink.toElementTooltipLink(target, text).toString());
        }
    }

    /**
     * Consulo stand-in for JB {@code PyPsiBundle.problemMessage(key, params)}: {@code template} produces the localized value
     * for the bundle entry (typically a {@code PyLocalize} method reference), its raw template is looked up and
     * formatted twice — once for the Problems-view description (backticks become single quotes) and once for the
     * editor hover tooltip (backticks become {@code <code>} blocks).
     */
    public static ProblemMessage problemMessage(Function<Object[], LocalizeValue> template, Object... params) {
        LocalizeValue value = template.apply(params);
        Optional<LocalizeKey> key = value.getKey();
        String rawTemplate = key.isPresent() ? LocalizeManager.get().getUnformattedText(key.get()).getValue() : null;
        if (rawTemplate == null) {
            String text = value.get();
            return new ProblemMessage(text, text);
        }
        return formatTemplate(rawTemplate, params);
    }

    /**
     * Same as {@link #problemMessage} but takes the already-resolved raw template. Visible for unit testing the
     * transformation without touching the bundle layer.
     */
    public static ProblemMessage formatTemplate(String rawTemplate, @Nullable Object... params) {
        String description = MessageFormat.format(toDescriptionTemplate(rawTemplate), forDescription(params));
        boolean anyCodified = false;
        for (Object param : params) {
            if (param instanceof CodifiedParam) {
                anyCodified = true;
                break;
            }
        }
        if (rawTemplate.indexOf('`') < 0 && !anyCodified) {
            // No code-rendered spans anywhere: description and tooltip are the same plain text.
            return new ProblemMessage(description, description);
        }
        String tooltip = "<html>" + MessageFormat.format(toTooltipTemplate(rawTemplate), forTooltip(params)) + "</html>";
        return new ProblemMessage(description, tooltip);
    }

    private record Rendered(String text, boolean balanced) {
    }

    @FunctionalInterface
    private interface DelimiterHandler {
        void onDelimiter(StringBuilder sb, boolean opening);
    }

    /**
     * Walks the receiver, copying ordinary characters verbatim. A doubled {@code ``} is an escape that emits a
     * single literal backtick; every remaining single {@code `} is a code-span delimiter that invokes
     * {@code onDelimiter} (its flag is {@code true} when the span is opening). Returns the rendered text paired with
     * whether the delimiters were balanced — an odd number signals a malformed template.
     */
    private static Rendered renderBackticks(String s, DelimiterHandler onDelimiter) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        boolean inCode = false;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '`' && i + 1 < s.length() && s.charAt(i + 1) == '`') {
                sb.append('`'); // `` -> a single literal backtick
                i += 2;
            }
            else if (c == '`') {
                onDelimiter.onDelimiter(sb, !inCode);
                inCode = !inCode;
                i++;
            }
            else {
                sb.append(c);
                i++;
            }
        }
        return new Rendered(sb.toString(), !inCode);
    }

    /**
     * Renders every code-span delimiter as {@code ''} so MessageFormat emits a literal single quote there, and
     * every doubled {@code ``} as a literal backtick. Apostrophes already present in the template (typically
     * doubled as {@code ''} for possessives like {@code can''t}) are preserved. Delimiter balance is irrelevant here:
     * an unbalanced delimiter just yields an unbalanced quote, exactly as MessageFormat would otherwise.
     */
    private static String toDescriptionTemplate(String s) {
        return renderBackticks(s, (sb, opening) -> sb.append("''")).text();
    }

    /**
     * Renders code-span delimiters as alternating {@code <code>} / {@code </code>} and every doubled {@code ``} as a literal
     * backtick. An odd number of delimiters signals a malformed template; fall back to a single-quote
     * rendering rather than emit an unclosed {@code <code>} into the tooltip.
     */
    private static String toTooltipTemplate(String s) {
        Rendered rendered = renderBackticks(s, (sb, opening) -> sb.append(opening ? "<code>" : "</code>"));
        return rendered.balanced() ? rendered.text() : renderBackticks(s, (sb, opening) -> sb.append("'")).text();
    }

    /**
     * Converts an already-formatted message that marks code-like spans with backticks into an HTML fragment
     * (no {@code <html>} wrapper): ordinary text is XML-escaped and each {@code `…`} pair becomes {@code <code>…</code>};
     * a doubled {@code ``} is a literal backtick. Use for strings that are produced <i>after</i> parameter
     * substitution — e.g. a type-mismatch breakdown node built from a
     * backtick template — so it cannot distinguish template backticks from value backticks (callers pass
     * names that do not contain backticks). An unbalanced delimiter is tolerated by closing the span.
     */
    public static String codeSpansToHtmlFragment(String message) {
        StringBuilder sb = new StringBuilder(message.length() + 16);
        StringBuilder text = new StringBuilder();
        Consumer<Void> flushText = v -> {
            if (!text.isEmpty()) {
                sb.append(XmlStringUtil.escapeString(text.toString()));
                text.setLength(0);
            }
        };
        boolean inCode = false;
        int i = 0;
        while (i < message.length()) {
            char c = message.charAt(i);
            if (c == '`' && i + 1 < message.length() && message.charAt(i + 1) == '`') {
                text.append('`'); // `` -> a single literal backtick
                i += 2;
            }
            else if (c == '`') {
                flushText.accept(null);
                sb.append(inCode ? "</code>" : "<code>");
                inCode = !inCode;
                i++;
            }
            else {
                text.append(c);
                i++;
            }
        }
        flushText.accept(null);
        if (inCode) {
            sb.append("</code>");
        }
        return sb.toString();
    }

    /**
     * The tooltip form of {@code message} as an HTML fragment for embedding inside a larger tooltip (e.g. as the
     * headline above a type-mismatch breakdown). {@link #formatTemplate} wraps code-bearing tooltips in {@code <html>…</html>};
     * this strips that wrapper. A plain message (no code spans) is XML-escaped so it is safe to embed verbatim.
     */
    public static String tooltipFragment(ProblemMessage message) {
        return message.tooltip().startsWith("<html>")
            ? removeSurrounding(message.tooltip(), "<html>", "</html>")
            : XmlStringUtil.escapeString(message.description());
    }

    private static @Nullable Object[] forDescription(@Nullable Object[] params) {
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object it = params[i];
            result[i] = it instanceof CodifiedParam codifiedParam ? codifiedParam.description() : it;
        }
        return result;
    }

    private static @Nullable Object[] forTooltip(@Nullable Object[] params) {
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object it = params[i];
            if (it instanceof CodifiedParam codifiedParam) {
                result[i] = codifiedParam.tooltip();
            }
            else if (it instanceof String s) {
                result[i] = XmlStringUtil.escapeString(s);
            }
            else {
                result[i] = it;
            }
        }
        return result;
    }

    // Kotlin String.removeSurrounding(prefix, suffix)
    static String removeSurrounding(String s, String prefix, String suffix) {
        if (s.length() >= prefix.length() + suffix.length() && s.startsWith(prefix) && s.endsWith(suffix)) {
            return s.substring(prefix.length(), s.length() - suffix.length());
        }
        return s;
    }
}
