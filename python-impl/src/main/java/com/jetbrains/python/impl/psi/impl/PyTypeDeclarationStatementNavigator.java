// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public final class PyTypeDeclarationStatementNavigator {
    private PyTypeDeclarationStatementNavigator() {
    }

    public static boolean isTypeDeclarationTarget(PsiElement element) {
        return getStatementByTarget(element) != null;
    }

    public static @Nullable PyTypeDeclarationStatement getStatementByTarget(PsiElement element) {
        PyTypeDeclarationStatement statement = as(element.getParent(), PyTypeDeclarationStatement.class);
        return statement != null && statement.getTarget() == element ? statement : null;
    }
}
