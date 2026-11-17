// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstKeywordPattern;
import org.jspecify.annotations.Nullable;

public interface PyKeywordPattern extends PyAstKeywordPattern, PyPattern {
    @Override
    @RequiredReadAction
    default @Nullable PyPattern getValuePattern() {
        return (PyPattern)PyAstKeywordPattern.super.getValuePattern();
    }

    @RequiredReadAction
    default @Nullable PyClassPattern getContainingClassPattern() {
        return getParent().getParent() instanceof PyClassPattern classPattern ? classPattern : null;
    }
}
