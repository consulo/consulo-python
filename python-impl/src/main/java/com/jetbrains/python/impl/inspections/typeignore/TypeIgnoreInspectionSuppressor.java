// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.impl.inspections.PySuppressionUtil;
import com.jetbrains.python.impl.inspections.quickfix.PySuppressWithTypeIgnoreFix;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.InspectionSuppressor;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Suppresses inspections on lines (or whole files) annotated with a {@code # type: ignore} or {@code # pycharm: ignore}
 * comment.
 * <p>
 * A code in brackets can be one of these:
 * <ul>
 *  <li>a suppress id ({@code PyTypeChecker}) or its kebab-case alias ({@code unresolved-references}), with an optional
 *    {@code pycharm:} prefix. It suppresses the whole inspection.</li>
 *  <li>a granular type-checker code ({@code unsupported-operator}). {@code com.jetbrains.python.impl.inspections.PyTypeCheckerProblemReporter}
 *    applies it. Here it only counts as a known code.</li>
 *  <li>a PyCharm code that names nothing, such as {@code pycharm:amongus}. It suppresses nothing.
 *    {@link PyUnknownIgnoreCodeInspection} reports it.</li>
 *  <li>a foreign code, such as mypy's {@code attr-defined}. It is not a known code.</li>
 * </ul>
 * A comment that names no PyCharm code suppresses every inspection on the line. The one exception is
 * {@link PyTypeIgnoreWithoutCodeInspection}, which reports such a comment. Every bare code of a {@code # pycharm: ignore}
 * comment is a PyCharm code.
 */
@ExtensionImpl
public class TypeIgnoreInspectionSuppressor implements InspectionSuppressor {

    @Override
    @RequiredReadAction
    public boolean isSuppressedFor(PsiElement element, String toolId) {
        if (element instanceof PsiFile) {
            return false;
        }
        PsiFile containingFile = element.getContainingFile();
        if (!(containingFile instanceof PyFile)) {
            return false;
        }

        PsiComment sameLineComment = PyPsiUtils.findSameLineComment(element);
        if (sameLineComment != null && suppresses(sameLineComment, toolId)) {
            return true;
        }
        for (PsiComment comment : TypeIgnoreComments.leadingFileLevelComments(containingFile)) {
            if (suppresses(comment, toolId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, String toolId) {
        // PyTypeChecker offers a suppress action per granular code, see PyTypeCheckerSuppressableProblemGroup.
        if (PySuppressionUtil.isCustomManaged(toolId)) {
            return SuppressQuickFix.EMPTY_ARRAY;
        }
        return new SuppressQuickFix[]{PySuppressWithTypeIgnoreFix.forInspection(toolId)};
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static boolean suppresses(PsiComment comment, String toolId) {
        PyIgnoreCommentUtil.ParsedIgnore parsed = PyIgnoreCommentUtil.parse(comment);
        if (parsed == null) {
            return false;
        }
        boolean namesPyCharmCode = false;
        for (PyIgnoreCommentUtil.CodeRef ref : PyIgnoreCommentUtil.codeRefs(parsed)) {
            PyIgnoreCodeResolver.Resolution resolution = PyIgnoreCodeResolver.resolve(ref);
            if (resolution instanceof PyIgnoreCodeResolver.Resolution.Inspection inspection) {
                if (inspection.suppressId().equals(toolId)) {
                    return true;
                }
            }
            else if (resolution instanceof PyIgnoreCodeResolver.Resolution.Foreign) {
                continue;
            }
            namesPyCharmCode = true;
        }
        if (namesPyCharmCode) {
            return false;
        }
        return !toolId.equals(PyTypeIgnoreWithoutCodeInspection.SUPPRESS_ID);
    }
}
