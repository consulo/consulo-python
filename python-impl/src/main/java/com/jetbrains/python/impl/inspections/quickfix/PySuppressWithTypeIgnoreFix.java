// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.impl.inspections.PySuppressionUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.document.Document;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * "Suppress with {@code # type: ignore}" quick fix (PY-90780): appends a trailing same-line {@code # type: ignore[<code>]}
 * comment to the offending line. Two advanced settings, read at apply time, control the inserted text:
 * {@link #INCLUDE_CODE_SETTING} (a code or a bare comment) and {@link #PYCHARM_NAMESPACE_SETTING} (the {@code pycharm:} prefix).
 * If a {@code # type: ignore} / {@code # pycharm: ignore} comment already exists on the line, the code is merged into its
 * brackets; other trailing comments are kept.
 * <p>
 * Use {@link #forInspection} for a whole inspection (inserts its kebab-case alias, e.g. {@code unresolved-references}) or
 * {@link #forCode} for a granular type-checker code (e.g. {@code unsupported-operator}).
 */
public class PySuppressWithTypeIgnoreFix implements SuppressQuickFix {
    /**
     * The advanced setting that adds the inspection code in brackets. When it is off, the fix writes a bare comment.
     */
    public static final String INCLUDE_CODE_SETTING = "python.type.ignore.suppress.include.code";

    /**
     * The advanced setting that adds the {@code pycharm:} namespace to the code.
     */
    public static final String PYCHARM_NAMESPACE_SETTING = "python.type.ignore.suppress.pycharm.namespace";

    private final String code;

    private PySuppressWithTypeIgnoreFix(String code) {
        this.code = code;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.inspPythonSuppressorSuppressWithTypeIgnore();
    }

    @Override
    public boolean isAvailable(Project project, PsiElement context) {
        return context.isValid();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        if (!element.isValid()) {
            return;
        }

        String namespacedCode = getBooleanSetting(PYCHARM_NAMESPACE_SETTING) ? "pycharm:" + code : code;
        PsiComment existing = PyPsiUtils.findSameLineComment(element);
        if (existing != null) {
            String merged = mergeInto(existing, namespacedCode);
            if (merged == null) {
                return;
            }
            LanguageLevel level = LanguageLevel.forElement(element);
            existing.replace(PyElementGenerator.getInstance(project).createFromText(level, PsiComment.class, merged));
        }
        else {
            appendTrailingComment(project, element, buildComment(namespacedCode));
        }
    }

    /**
     * A new comment. It is bare when {@link #INCLUDE_CODE_SETTING} is off.
     */
    private static String buildComment(String namespacedCode) {
        return getBooleanSetting(INCLUDE_CODE_SETTING) ? "# type: ignore[" + namespacedCode + "]" : "# type: ignore";
    }

    /**
     * Text that should replace {@code existing}, or {@code null} when nothing changes. The fix adds the code to an existing
     * ignore comment even when {@link #INCLUDE_CODE_SETTING} is off. That comment names a PyCharm code, because the problem
     * is still reported, so a bare comment would suppress more. A {@code # pycharm: ignore} comment gets the code without
     * the namespace. Any other comment stays after a new ignore comment.
     */
    private @Nullable String mergeInto(PsiComment existing, String namespacedCode) {
        PyIgnoreCommentUtil.ParsedIgnore parsed = PyIgnoreCommentUtil.parse(existing);
        if (parsed == null) {
            return buildComment(namespacedCode) + "  " + existing.getText();
        }
        boolean isPyCharmDirective = parsed.directive() == PyIgnoreCommentUtil.Directive.PYCHARM;
        String keyword = isPyCharmDirective ? "pycharm" : "type";
        Set<String> codes = new LinkedHashSet<>(parsed.rawCodes());
        if (!codes.add(isPyCharmDirective ? code : namespacedCode)) {
            return null;  // already suppressed with this code
        }
        return "# " + keyword + ": ignore[" + String.join(", ", codes) + "]";
    }

    private static void appendTrailingComment(Project project, PsiElement element, String comment) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getDocument(file);
        if (document == null) {
            return;
        }
        int line = document.getLineNumber(element.getTextRange().getEndOffset());
        document.insertString(document.getLineEndOffset(line), "  " + comment);
        documentManager.commitDocument(document);
    }

    private static boolean getBooleanSetting(String id) {
        // TODO consulo: AdvancedSettings missing, the defaults of the JB <advancedSetting> registrations are used
        return switch (id) {
            case INCLUDE_CODE_SETTING -> true;
            case PYCHARM_NAMESPACE_SETTING -> false;
            default -> false;
        };
    }

    /**
     * A fix for a whole inspection; inserts its kebab-case alias (or the raw id when it has none).
     */
    public static PySuppressWithTypeIgnoreFix forInspection(String toolId) {
        String suppressionCode = PySuppressionUtil.toSuppressionCode(toolId);
        return new PySuppressWithTypeIgnoreFix(suppressionCode != null ? suppressionCode : toolId);
    }

    /**
     * A fix for a specific code (e.g. a granular {@link com.jetbrains.python.impl.inspections.PyTypeCheckerSuppressionCode}).
     */
    public static PySuppressWithTypeIgnoreFix forCode(String code) {
        return new PySuppressWithTypeIgnoreFix(code);
    }
}
