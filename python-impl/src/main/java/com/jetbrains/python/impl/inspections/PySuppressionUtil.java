// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for Python {@code # noinspection <id>} suppression comments.
 * <p>
 * Used by {@code PyInspectionsSuppressor} (whole-inspection ids such as {@code PyMethodOverriding} and their kebab-case
 * aliases such as {@code method-overriding}) and by {@link PyTypeCheckerProblemReporter} (the granular
 * {@link PyTypeCheckerSuppressionCode} ids), so the scope-scanning stays in one place. {@link #toSuppressionCode} is also
 * reused by the {@code # type: ignore} suppressor to recognize the same kebab aliases inside brackets.
 */
public final class PySuppressionUtil {
    private PySuppressionUtil() {
    }

    private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);

    /**
     * {@code PyTypeChecker} is excluded from the blanket alias mechanism: it fully manages its own granular suppression
     * codes ({@link PyTypeCheckerSuppressionCode}), so it gets no auto-derived {@code type-checker} alias and keeps the legacy
     * {@code PyTypeChecker} id as its catch-all.
     */
    private static final String PY_TYPE_CHECKER_INSPECTION_ID = "PyTypeChecker";

    private static final Pattern UPPER_RUN_BEFORE_WORD = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private static final Pattern LOWER_BEFORE_UPPER = Pattern.compile("([a-z0-9])([A-Z])");

    /**
     * Returns {@code true} if {@code element} is covered by a {@code # noinspection <suppressId>} comment placed above its
     * enclosing statement, function, or class.
     */
    public static boolean isSuppressed(PsiElement element, String suppressId) {
        // For a statement list, check if the suppression comment is placed inside it,
        // before the first statement (e.g., in an else or match-case block body).
        if (element instanceof PyStatementList statementList && isSuppressedStatementListBody(statementList, suppressId)) {
            return true;
        }
        return isSuppressedForParent(element, PyStatement.class, suppressId) ||
            isSuppressedForParent(element, PyFunction.class, suppressId) ||
            isSuppressedForParent(element, PyClass.class, suppressId);
    }

    /**
     * {@code true} for inspections that fully manage their own suppression UI (currently only {@code PyTypeChecker}, via
     * {@code PyTypeCheckerSuppressableProblemGroup}). {@code PyInspectionsSuppressor} offers no blanket suppress actions for
     * them, leaving the per-problem {@code SuppressableProblemGroup} as the sole source.
     */
    public static boolean isCustomManaged(String toolId) {
        return toolId.equals(PY_TYPE_CHECKER_INSPECTION_ID);
    }

    /**
     * Maps a legacy inspection id to its industry-standard kebab-case suppression alias by dropping the {@code Py}
     * product prefix and converting CamelCase to {@code kebab-case} (e.g. {@code PyMethodOverriding} -> {@code method-overriding},
     * {@code PyUnresolvedReferences} -> {@code unresolved-references}).
     * <p>
     * Returns {@code null} for ids that are not {@code Py}-prefixed inspection ids (e.g. platform inspections that also run
     * on Python files), which keep their original id only, and for {@link #PY_TYPE_CHECKER_INSPECTION_ID}.
     */
    public static @Nullable String toSuppressionCode(String toolId) {
        if (toolId.equals(PY_TYPE_CHECKER_INSPECTION_ID)) {
            return null;
        }
        if (toolId.length() < 3 || !toolId.startsWith("Py") || !Character.isUpperCase(toolId.charAt(2))) {
            return null;
        }
        String result = toolId.substring(2);
        result = UPPER_RUN_BEFORE_WORD.matcher(result).replaceAll("$1-$2");
        result = LOWER_BEFORE_UPPER.matcher(result).replaceAll("$1-$2");
        return result.toLowerCase();
    }

    private static boolean isSuppressedStatementListBody(PyStatementList list, String suppressId) {
        PyStatement[] statements = list.getStatements();
        if (statements.length == 0) {
            return false;
        }
        PyStatement firstStatement = statements[0];
        if (!(PsiTreeUtil.skipWhitespacesBackward(firstStatement) instanceof PsiComment comment)) {
            return false;
        }
        String text = comment.getText();
        return isSuppressedInComment((text.startsWith("#") ? text.substring(1) : text).trim(), suppressId);
    }

    private static boolean isSuppressedForParent(PsiElement element, Class<? extends PyElement> parentClass, String suppressId) {
        PyElement parent = PsiTreeUtil.getParentOfType(element, parentClass, false);
        if (parent == null) {
            return false;
        }
        return isSuppressedForElement(parent, suppressId);
    }

    private static boolean isSuppressedForElement(PyElement stmt, String suppressId) {
        PsiElement prevSibling = stmt.getPrevSibling();
        if (prevSibling == null) {
            PsiElement parent = stmt.getParent();
            prevSibling = parent != null ? parent.getPrevSibling() : null;
        }
        while (prevSibling instanceof PsiComment || prevSibling instanceof PsiWhiteSpace) {
            if (prevSibling instanceof PsiComment && isSuppressedInComment(prevSibling.getText().substring(1).trim(), suppressId)) {
                return true;
            }
            prevSibling = prevSibling.getPrevSibling();
        }
        return false;
    }

    private static boolean isSuppressedInComment(String commentText, String suppressId) {
        Matcher m = SUPPRESS_PATTERN.matcher(commentText);
        return m.matches() && SuppressionUtil.isInspectionToolIdMentioned(m.group(1), suppressId);
    }
}
