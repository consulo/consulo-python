// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;

import java.util.List;

public record PyPackageInstallAllProblemInfo(
    PsiElement psiElement,
    PyInspectionMessages.ProblemMessage message,
    ProblemHighlightType highlightType,
    String refName,
    List<LocalQuickFix> fixes
) {
}
