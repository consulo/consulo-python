// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.impl.inspections.PyInspection;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reports a PyCharm code in a {@code # type: ignore} or {@code # pycharm: ignore} comment that names no inspection.
 * <p>
 * A PyCharm code has the {@code pycharm:} namespace, or it stands in a {@code # pycharm: ignore} comment. Such a code
 * suppresses nothing. A bare code in {@code # type: ignore} is not reported, because other type checkers use their
 * own codes there. A name that looks like a suppress id, such as {@code PyFoo}, is not reported either. See
 * {@link PyIgnoreCodeResolver.Resolution.Unknown}.
 */
@ExtensionImpl
public class PyUnknownIgnoreCodeInspection extends PyInspection {

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnknownIgnoreCode();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
            @Override
            @RequiredReadAction
            public void visitComment(PsiComment comment) {
                PyIgnoreCommentUtil.IgnoreCodes codes = PyIgnoreCommentUtil.parseCodes(comment);
                if (codes == null) {
                    return;
                }
                List<PyIgnoreCommentUtil.@Nullable CodeRef> refs = new ArrayList<>();
                List<PyIgnoreCodeResolver.@Nullable Resolution> resolutions = new ArrayList<>();
                for (PyIgnoreCommentUtil.CodeOccurrence occurrence : codes.occurrences()) {
                    PyIgnoreCommentUtil.CodeRef ref = PyIgnoreCommentUtil.codeRef(codes.directive(), occurrence.rawCode());
                    refs.add(ref);
                    resolutions.add(ref != null ? PyIgnoreCodeResolver.resolve(ref) : null);
                }
                for (int index = 0; index < codes.occurrences().size(); index++) {
                    PyIgnoreCommentUtil.CodeOccurrence occurrence = codes.occurrences().get(index);
                    PyIgnoreCommentUtil.CodeRef ref = refs.get(index);
                    if (ref == null) {
                        continue;
                    }
                    if (!(resolutions.get(index) instanceof PyIgnoreCodeResolver.Resolution.Unknown)) {
                        continue;
                    }
                    LocalQuickFix[] fixes = canRemove(resolutions, index)
                        ? new LocalQuickFix[]{new RemoveIgnoreCodeFix(index)}
                        : LocalQuickFix.EMPTY_ARRAY;
                    holder.newProblem(PyLocalize.inspUnknownIgnoreCode(ref.name()))
                        .range(comment, occurrence.range())
                        .withFixes(fixes)
                        .create();
                }
            }
        };
    }

    /**
     * The fix can remove the code at {@code index} when no code stays, or when a code that stays names a PyCharm code.
     * Only foreign codes left would make the comment suppress every inspection on its line.
     */
    private static boolean canRemove(List<PyIgnoreCodeResolver.@Nullable Resolution> resolutions, int index) {
        List<PyIgnoreCodeResolver.@Nullable Resolution> remaining = new ArrayList<>();
        for (int i = 0; i < resolutions.size(); i++) {
            if (i != index) {
                remaining.add(resolutions.get(i));
            }
        }
        return remaining.isEmpty() || remaining.stream().anyMatch(it -> it != null && !(it instanceof PyIgnoreCodeResolver.Resolution.Foreign));
    }

    /**
     * Removes the code at {@code index} from the brackets of an ignore comment. When no other code stays, the fix removes
     * the whole comment instead, because a bare comment suppresses every inspection on its line. The text after the
     * brackets stays as a plain comment.
     */
    // Consulo: JB extends PsiUpdateModCommandQuickFix (ModCommand API missing), ported as a classic LocalQuickFix
    private static class RemoveIgnoreCodeFix implements LocalQuickFix {
        private final int myIndex;

        RemoveIgnoreCodeFix(int index) {
            myIndex = index;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspUnknownIgnoreCodeRemoveFix();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiComment comment)) {
                return;
            }
            PyIgnoreCommentUtil.IgnoreCodes codes = PyIgnoreCommentUtil.parseCodes(comment);
            if (codes == null) {
                return;
            }
            TextRange bracketContent = codes.bracketContent();
            if (bracketContent == null) {
                return;
            }
            if (myIndex >= codes.occurrences().size()) {
                return;
            }
            String text = comment.getText();
            List<PyIgnoreCommentUtil.CodeOccurrence> remaining = new ArrayList<>();
            for (int i = 0; i < codes.occurrences().size(); i++) {
                if (i != myIndex) {
                    remaining.add(codes.occurrences().get(i));
                }
            }
            if (!remaining.isEmpty()) {
                String newCodes = remaining.stream().map(PyIgnoreCommentUtil.CodeOccurrence::rawCode).collect(Collectors.joining(", "));
                replaceComment(project, comment, bracketContent.replace(text, newCodes));
                return;
            }
            String tail = text.substring(bracketContent.getEndOffset() + 1).trim();
            if (tail.isEmpty()) {
                if (comment.getPrevSibling() instanceof PsiWhiteSpace whiteSpace && !whiteSpace.textContains('\n')) {
                    whiteSpace.delete();
                }
                comment.delete();
            }
            else if (tail.startsWith("#")) {
                replaceComment(project, comment, tail);
            }
            else {
                replaceComment(project, comment, "# " + tail);
            }
        }

        private static void replaceComment(Project project, PsiComment comment, String newText) {
            LanguageLevel level = LanguageLevel.forElement(comment);
            comment.replace(PyElementGenerator.getInstance(project).createFromText(level, PsiComment.class, newText));
        }
    }
}
