// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyDelStatement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

public final class PyDelStatementNavigator {
    private PyDelStatementNavigator() {
    }

    public static @Nullable PyDelStatement getDelStatementByTarget(PsiElement target) {
        PyDelStatement statement = PsiTreeUtil.getParentOfType(target, PyDelStatement.class);
        if (statement == null) {
            return null;
        }
        return ArrayUtil.contains(target, statement.getTargets()) ? statement : null;
    }
}
