// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiReference;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyImportCandidateProvider {
    ExtensionPointName<PyImportCandidateProvider> EP_NAME = ExtensionPointName.create(PyImportCandidateProvider.class);

    @RequiredReadAction
    void addImportCandidates(PsiReference reference, String name, AutoImportQuickFix quickFix);
}
