// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.psi.PyGlobalStatement;
import org.jspecify.annotations.Nullable;

public final class PyGlobalStatementNavigator {
    private PyGlobalStatementNavigator() {
    }

    public static @Nullable PyGlobalStatement getByArgument(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof PyGlobalStatement statement) {
            return ArrayUtil.find(statement.getGlobals(), element) != -1 ? statement : null;
        }
        return null;
    }
}
