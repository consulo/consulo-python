// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.PyCallExpression;
import org.jspecify.annotations.Nullable;

public final class PyCallExpressionNavigator {
    private PyCallExpressionNavigator() {
    }

    public static @Nullable PyCallExpression getPyCallExpressionByCallee(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof PyCallExpression expression) {
            return expression.getCallee() == element ? expression : null;
        }
        return null;
    }
}
