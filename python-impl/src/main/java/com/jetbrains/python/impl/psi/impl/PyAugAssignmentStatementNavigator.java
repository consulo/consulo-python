// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import org.jspecify.annotations.Nullable;

public final class PyAugAssignmentStatementNavigator {
    private PyAugAssignmentStatementNavigator() {
    }

    public static @Nullable PyAugAssignmentStatement getStatementByTarget(PsiElement element) {
        PyAugAssignmentStatement statement = PsiTreeUtil.getParentOfType(element, PyAugAssignmentStatement.class);
        if (statement == null) {
            return null;
        }
        return statement.getTarget() == element ? statement : null;
    }
}
