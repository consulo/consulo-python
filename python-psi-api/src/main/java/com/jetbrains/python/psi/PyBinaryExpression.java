// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstBinaryExpression;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;


public interface PyBinaryExpression extends PyAstBinaryExpression, PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

    @Override
    @RequiredReadAction
    default PyExpression getLeftExpression() {
        return (PyExpression)PyAstBinaryExpression.super.getLeftExpression();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getRightExpression() {
        return (PyExpression)PyAstBinaryExpression.super.getRightExpression();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getQualifier() {
        return (PyExpression)PyAstBinaryExpression.super.getQualifier();
    }
}
