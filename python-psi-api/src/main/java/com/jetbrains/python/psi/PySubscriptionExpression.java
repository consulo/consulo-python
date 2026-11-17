// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstSubscriptionExpression;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PySubscriptionExpression
    extends PyAstSubscriptionExpression, PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

    @Override
    @RequiredReadAction
    default List<PyExpression> getArguments() {
        //noinspection unchecked
        return (List)PyAstSubscriptionExpression.super.getArguments();
    }

    /**
     * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
     */
    @Override
    @RequiredReadAction
    default PyExpression getRootOperand() {
        return (PyExpression)PyAstSubscriptionExpression.super.getRootOperand();
    }

    @Override
    @RequiredReadAction
    default PyExpression getOperand() {
        return (PyExpression)PyAstSubscriptionExpression.super.getOperand();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getIndexExpression() {
        return (PyExpression)PyAstSubscriptionExpression.super.getIndexExpression();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getQualifier() {
        return (PyExpression)PyAstSubscriptionExpression.super.getQualifier();
    }
}
