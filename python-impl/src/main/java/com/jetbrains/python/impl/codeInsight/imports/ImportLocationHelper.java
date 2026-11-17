// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

@ServiceAPI(ComponentScope.APPLICATION)
public interface ImportLocationHelper {

    @Nullable PsiElement getSearchStartPosition(@Nullable PsiElement anchor, PsiElement insertParent);

    static ImportLocationHelper getInstance() {
        return Application.get().getInstance(ImportLocationHelper.class);
    }
}
