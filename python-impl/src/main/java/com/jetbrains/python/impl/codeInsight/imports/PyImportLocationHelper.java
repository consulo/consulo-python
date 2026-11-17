// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Declared in JB {@code ImportLocationHelper.kt}.
 */
@ServiceImpl
@Singleton
public class PyImportLocationHelper implements ImportLocationHelper {
    @Override
    public @Nullable PsiElement getSearchStartPosition(@Nullable PsiElement anchor, PsiElement insertParent) {
        return insertParent.getFirstChild();
    }
}
