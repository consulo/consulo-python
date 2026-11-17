// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstConditionalExpression;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;


public interface PyConditionalExpression extends PyAstConditionalExpression, PyExpression {
    @Override
    @RequiredReadAction
    default PyExpression getTruePart() {
        return (PyExpression)PyAstConditionalExpression.super.getTruePart();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getCondition() {
        return (PyExpression)PyAstConditionalExpression.super.getCondition();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getFalsePart() {
        return (PyExpression)PyAstConditionalExpression.super.getFalsePart();
    }
}
