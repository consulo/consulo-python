// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyIfStatement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public final class PyIfStatementNavigator {
    private PyIfStatementNavigator() {
    }

    public static @Nullable PyIfStatement getIfStatementByIfKeyword(@Nullable PsiElement ifKeyword) {
        PyIfStatement statement = PsiTreeUtil.getParentOfType(ifKeyword, PyIfStatement.class);
        if (statement == null) {
            return null;
        }
        return statement.getIfPart().getFirstChild() == ifKeyword ? statement : null;
    }
}
