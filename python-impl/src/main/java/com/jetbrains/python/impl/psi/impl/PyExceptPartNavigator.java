// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import org.jspecify.annotations.Nullable;

public final class PyExceptPartNavigator {
    private PyExceptPartNavigator() {
    }

    public static @Nullable PyExceptPart getPyExceptPartByTarget(PsiElement element) {
        PyExceptPart pyExceptPart = PsiTreeUtil.getParentOfType(element, PyExceptPart.class, false);
        if (pyExceptPart == null) {
            return null;
        }
        PyExpression expr = pyExceptPart.getTarget();
        if (expr != null && PsiTreeUtil.isAncestor(expr, element, false)) {
            return pyExceptPart;
        }
        return null;
    }
}
