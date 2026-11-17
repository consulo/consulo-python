// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.InspectionSuppressor;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO consulo: RedundantSuppressionDetector missing (getSuppressionIds / isSuppressionFor / createRemoveRedundantSuppressionFix)
@ExtensionImpl
public final class PyInspectionsSuppressor implements InspectionSuppressor {
    private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);
    private static final String PY_INCORRECT_DOCSTRING_INSPECTION_ID = new PyIncorrectDocstringInspection().getID();
    private static final String PY_MISSING_OR_EMPTY_DOCSTRING_INSPECTION_ID = new PyMissingOrEmptyDocstringInspection().getID();

    @Override
    public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, String toolId) {
        // PyTypeChecker owns its suppression UI entirely (PyTypeCheckerSuppressableProblemGroup); offer no blanket
        // actions for it, so the only suppressions shown are its granular per-code ones.
        if (PySuppressionUtil.isCustomManaged(toolId)) {
            return SuppressQuickFix.EMPTY_ARRAY;
        }
        // Insert the industry-standard kebab-case alias (e.g. `method-overriding`) when the inspection has one,
        // so that is the form users adopt; the legacy `PyMethodOverriding` id keeps working for recognition.
        String suppressId = suppressId(toolId);
        // Docstring inspections only attach to declarations, so they offer no per-statement suppression.
        boolean includeStatement = !PY_INCORRECT_DOCSTRING_INSPECTION_ID.equals(toolId) &&
            !PY_MISSING_OR_EMPTY_DOCSTRING_INSPECTION_ID.equals(toolId);
        return createSuppressActions(suppressId, element, includeStatement);
    }

    /**
     * Builds the "Suppress for this statement / for function 'f' / for class 'C'" quick fixes that insert a
     * {@code # noinspection <suppressId>} comment. Shared with {@link PyTypeCheckerSuppressableProblemGroup},
     * which passes one of its granular codes as {@code suppressId} but reuses the same declaration-naming wording.
     */
    public static SuppressQuickFix[] createSuppressActions(String suppressId,
                                                           @Nullable PsiElement element,
                                                           boolean includeStatement) {
        List<SuppressQuickFix> fixes = new ArrayList<>(3);
        if (includeStatement) {
            fixes.add(new PySuppressInspectionFix(suppressId, PyLocalize.inspPythonSuppressorSuppressForStatement(), PyStatement.class) {
                @Override
                public PsiElement getContainer(PsiElement context) {
                    if (PsiTreeUtil.getParentOfType(context, PyStatementList.class, false, ScopeOwner.class) != null ||
                        PsiTreeUtil.getParentOfType(context, PyFunction.class, PyClass.class) == null) {
                        return super.getContainer(context);
                    }
                    return null;
                }
            });
        }
        fixes.add(new PySuppressInspectionFix(suppressId, functionText(element), PyFunction.class));
        fixes.add(new PySuppressInspectionFix(suppressId, classText(element), PyClass.class));
        return fixes.toArray(SuppressQuickFix.EMPTY_ARRAY);
    }

    // Name the enclosing function/class in the action text (like Kotlin), falling back to the generic wording
    // when there is no enclosing declaration (e.g. when the list is requested from the inspection tool window).
    private static LocalizeValue functionText(@Nullable PsiElement element) {
        PyFunction function = element == null ? null : PsiTreeUtil.getParentOfType(element, PyFunction.class);
        String name = function == null ? null : function.getName();
        return name != null
            ? PyLocalize.inspPythonSuppressorSuppressForFunctionNamed(name)
            : PyLocalize.inspPythonSuppressorSuppressForFunction();
    }

    private static LocalizeValue classText(@Nullable PsiElement element) {
        PyClass pyClass = element == null ? null : PsiTreeUtil.getParentOfType(element, PyClass.class);
        String name = pyClass == null ? null : pyClass.getName();
        return name != null
            ? PyLocalize.inspPythonSuppressorSuppressForClassNamed(name)
            : PyLocalize.inspPythonSuppressorSuppressForClass();
    }

    @Override
    public boolean isSuppressedFor(PsiElement element, String toolId) {
        if (PySuppressionUtil.isSuppressed(element, toolId)) {
            return true;
        }
        String code = PySuppressionUtil.toSuppressionCode(toolId);
        return code != null && PySuppressionUtil.isSuppressed(element, code);
    }

    private static String suppressId(String toolId) {
        String code = PySuppressionUtil.toSuppressionCode(toolId);
        return code != null ? code : toolId;
    }

    public @Nullable String getSuppressionIds(PsiElement element) {
        if (element instanceof PsiComment comment) {
            Matcher matcher = SUPPRESS_PATTERN.matcher(comment.getText().substring(1).trim());
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    public boolean isSuppressionFor(PsiElement elementWithSuppression, PsiElement place, String toolId) {
        return isSuppressionForParent(elementWithSuppression, place, PyStatement.class, toolId) ||
            isSuppressionForParent(elementWithSuppression, place, PyFunction.class, toolId) ||
            isSuppressionForParent(elementWithSuppression, place, PyClass.class, toolId);
    }

    private static boolean isSuppressionForParent(PsiElement elementWithSuppression,
                                                  PsiElement place,
                                                  Class<? extends PyStatement> parentClass,
                                                  String suppressId) {
        PyStatement parent = PsiTreeUtil.getParentOfType(place, parentClass, false);
        if (parent == null) {
            return false;
        }
        return findSuppressionComment(parent, suppressId) == elementWithSuppression;
    }

    private static @Nullable PsiComment findSuppressionComment(PyStatement stmt, String suppressId) {
        // The suppression comment lives above the statement, or above its enclosing block when the statement is
        // the first child of that block (e.g. the first statement in a function body).
        PsiElement anchor = stmt.getPrevSibling() != null ? stmt : stmt.getParent();
        if (anchor == null) {
            return null;
        }
        List<PsiComment> precedingComments = PyPsiUtils.getPrecedingComments(anchor, false);
        // Walk from the comment nearest the statement outwards, matching the closest suppression first.
        for (int i = precedingComments.size() - 1; i >= 0; i--) {
            PsiComment comment = precedingComments.get(i);
            if (isSuppressedInComment(comment.getText().substring(1).trim(), suppressId)) {
                return comment;
            }
        }
        return null;
    }

    private static boolean isSuppressedInComment(String commentText, String suppressId) {
        Matcher m = SUPPRESS_PATTERN.matcher(commentText);
        return m.matches() && SuppressionUtil.isInspectionToolIdMentioned(m.group(1), suppressId);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.getInstance();
    }
}
