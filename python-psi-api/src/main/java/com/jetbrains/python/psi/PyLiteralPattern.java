// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstLiteralPattern;

public interface PyLiteralPattern extends PyAstLiteralPattern, PyPattern {
    @Override
    @RequiredReadAction
    default PyExpression getExpression() {
        return (PyExpression)PyAstLiteralPattern.super.getExpression();
    }
}
