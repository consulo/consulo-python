// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstSliceItem;
import org.jspecify.annotations.Nullable;

public interface PySliceItem extends PyAstSliceItem, PyExpression {
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getLowerBound() {
        return (PyExpression)PyAstSliceItem.super.getLowerBound();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getUpperBound() {
        return (PyExpression)PyAstSliceItem.super.getUpperBound();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getStride() {
        return (PyExpression)PyAstSliceItem.super.getStride();
    }
}
