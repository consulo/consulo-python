// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.psi.PsiReference;

import java.util.List;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyUnresolvedReferenceQuickFixProvider {
    ExtensionPointName<PyUnresolvedReferenceQuickFixProvider> EP_NAME = ExtensionPointName.create(PyUnresolvedReferenceQuickFixProvider.class);

    /**
     * @param reference The reference containing an unresolved import.
     * @param existing  All already suggested quick fixes, including not only import fixes.
     */
    void registerQuickFixes(PsiReference reference, List<LocalQuickFix> existing);
}
