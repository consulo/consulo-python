// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import org.jspecify.annotations.Nullable;

public final class PyForStatementNavigator {
    private PyForStatementNavigator() {
    }

    public static @Nullable PyForStatement getPyForStatementByIterable(PsiElement element) {
        PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
        if (forStatement == null) {
            return null;
        }
        PyExpression target = forStatement.getForPart().getTarget();
        if (target != null && PsiTreeUtil.isAncestor(target, element, false)) {
            return forStatement;
        }
        return null;
    }

    public static @Nullable Object getPyForStatementByBody(PsiElement element) {
        PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
        if (forStatement == null) {
            return null;
        }
        PyForPart forPart = forStatement.getForPart();
        return forPart == element || forPart.getStatementList() == element ? forStatement : null;
    }
}
