// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.jetbrains.python.psi.PyElement;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PyUnresolvedReferenceQuickFixes {
    List<LocalQuickFix> getInstallPackageQuickFixes(PyElement node, PsiReference reference, String refName);

    @Nullable LocalQuickFix getInstallAllPackagesQuickFix(List<PyPackageInstallAllProblemInfo> unresolvedRefs);

    @Nullable LocalQuickFix getAddSourceRootQuickFix(PyElement node);

    List<LocalQuickFix> getAddIgnoredIdentifierQuickFixes(List<QualifiedName> qualifiedNames);

    List<LocalQuickFix> getImportStatementQuickFixes(PsiElement element);

    List<LocalQuickFix> getAutoImportFixes(PyElement node, PsiReference reference, PsiElement element);

    void getPluginQuickFixes(List<LocalQuickFix> fixes, PsiReference reference);
}
