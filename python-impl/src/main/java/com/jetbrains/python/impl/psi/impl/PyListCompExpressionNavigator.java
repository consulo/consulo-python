// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import org.jspecify.annotations.Nullable;

public final class PyListCompExpressionNavigator {
    private PyListCompExpressionNavigator() {
    }

    public static @Nullable PyListCompExpression getPyListCompExpressionByVariable(PsiElement element) {
        PyListCompExpression listCompExpression = PsiTreeUtil.getParentOfType(element, PyListCompExpression.class, false);
        if (listCompExpression == null) {
            return null;
        }
        for (PyComprehensionForComponent component : listCompExpression.getForComponents()) {
            PyExpression variable = component.getIteratorVariable();
            if (variable != null && PsiTreeUtil.isAncestor(variable, element, false)) {
                return listCompExpression;
            }
        }
        return null;
    }
}
