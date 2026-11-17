// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.impl.inspections.PyIgnoreCodeComputer;
import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.impl.inspections.PyInspection;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reports a {@code # type: ignore} or {@code # pycharm: ignore} comment that names no known inspection code.
 * <p>
 * Such a comment suppresses every inspection on its line, or in the whole file when it stands in the leading
 * comments of the file. It can therefore hide a problem that appears there later. The comment keeps this
 * effect. This inspection only asks the user to name the inspection codes. For a comment on a line of code,
 * the quick fix adds the codes of the problems on that line.
 */
@ExtensionImpl
public class PyTypeIgnoreWithoutCodeInspection extends PyInspection {
    public static final String SUPPRESS_ID = "PyTypeIgnoreWithoutCode";

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTypeIgnoreWithoutCode();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
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
                PyIgnoreCommentUtil.ParsedIgnore parsed = PyIgnoreCommentUtil.parse(comment);
                if (parsed == null) {
                    return;
                }
                if (PyIgnoreCodeResolver.namesPyCharmCode(parsed)) {
                    return;
                }
                TypeIgnoreComments.IgnoreScope scope = TypeIgnoreComments.ignoreScope(comment);
                if (scope == null) {
                    return;
                }
                switch (scope) {
                    case LINE -> registerProblem(comment, PyLocalize.inspTypeIgnoreWithoutCodeLine().get(), new AddInspectionCodesFix());
                    case FILE -> registerProblem(comment, PyLocalize.inspTypeIgnoreWithoutCodeFile().get());
                }
            }
        };
    }

    /**
     * Adds the codes of the problems on the line of the comment to its brackets. {@link PyIgnoreCodeComputer} finds the
     * codes. The fix keeps the codes that the comment already lists and the text after the brackets.
     */
    // Consulo: JB extends PsiUpdateModCommandQuickFix (ModCommand API missing), ported as a classic LocalQuickFix
    private static class AddInspectionCodesFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspTypeIgnoreAddCodeFix();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiComment comment)) {
                return;
            }
            PsiFile containingFile = comment.getContainingFile();
            if (containingFile == null) {
                return;
            }
            PsiFile originalFile = containingFile.getOriginalFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(originalFile);
            if (document == null) {
                return;
            }
            int lineNumber = document.getLineNumber(comment.getTextRange().getStartOffset());

            List<String> codes = PyIgnoreCodeComputer.codesOnLine(originalFile, lineNumber, Set.of(SUPPRESS_ID));
            String newText = mergeCodes(comment.getText(), codes);
            if (newText.equals(comment.getText())) {
                return;
            }

            LanguageLevel level = LanguageLevel.forElement(comment);
            comment.replace(PyElementGenerator.getInstance(project).createFromText(level, PsiComment.class, newText));
        }

        private static String mergeCodes(String commentText, List<String> codes) {
            if (codes.isEmpty()) {
                return commentText;
            }
            int ignoreIndex = commentText.toLowerCase(Locale.ROOT).indexOf("ignore");
            if (ignoreIndex < 0) {
                return commentText;
            }
            int afterIgnore = ignoreIndex + "ignore".length();
            int open = commentText.indexOf('[', afterIgnore);
            if (open < 0) {
                return commentText.substring(0, afterIgnore) + "[" + String.join(", ", codes) + "]" + commentText.substring(afterIgnore);
            }
            int close = commentText.indexOf(']', open);
            if (close < 0) {
                return commentText;
            }
            Set<String> merged = new LinkedHashSet<>();
            for (String it : commentText.substring(open + 1, close).split(",", -1)) {
                String c = it.trim();
                if (!c.isEmpty()) {
                    merged.add(c);
                }
            }
            merged.addAll(codes);
            return commentText.substring(0, open + 1) + String.join(", ", merged) + commentText.substring(close);
        }
    }
}
