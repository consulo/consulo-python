// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstKeyValuePattern;
import org.jspecify.annotations.Nullable;

public interface PyKeyValuePattern extends PyAstKeyValuePattern, PyPattern {
    @Override
    @RequiredReadAction
    default PyPattern getKeyPattern() {
        return (PyPattern)PyAstKeyValuePattern.super.getKeyPattern();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyPattern getValuePattern() {
        return (PyPattern)PyAstKeyValuePattern.super.getValuePattern();
    }
}
