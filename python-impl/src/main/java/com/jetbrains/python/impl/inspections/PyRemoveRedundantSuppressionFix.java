// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes a single inspection id from a `# noinspection` comment, deleting the whole comment line
 * when that id is the only one being suppressed. Produced by {@link PyInspectionsSuppressor} to clear up
 * suppressions reported as redundant by the platform `RedundantSuppression` inspection.
 */
// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
class PyRemoveRedundantSuppressionFix implements LocalQuickFix {
    private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);
    private static final Pattern ID_SEPARATOR = Pattern.compile("[,\\s]+");

    private final String myToolId;

    PyRemoveRedundantSuppressionFix(String toolId) {
        myToolId = toolId;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.inspRedundantSuppressionRemoveQuickfixName(myToolId);
    }

    // JB getFamilyName(): PyPsiBundle.message("INSP.redundant.suppression.remove.quickfix.family.name")
    // -> PyLocalize.inspRedundantSuppressionRemoveQuickfixFamilyName(); Consulo LocalQuickFix has no family name

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        PsiComment comment = element instanceof PsiComment psiComment
            ? psiComment
            : PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        if (comment == null) {
            return;
        }
        Matcher matcher = SUPPRESS_PATTERN.matcher(comment.getText().substring(1).trim());
        if (!matcher.matches()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (String id : ID_SEPARATOR.split(matcher.group(1))) {
            if (!id.isBlank() && !id.equals(myToolId)) {
                remaining.add(id);
            }
        }
        if (remaining.isEmpty()) {
            comment.delete();
        }
        else {
            PsiComment newComment = PyElementGenerator.getInstance(project)
                .createFromText(LanguageLevel.forElement(comment), PsiComment.class, "# noinspection " + String.join(", ", remaining));
            comment.replace(newComment);
        }
    }
}
