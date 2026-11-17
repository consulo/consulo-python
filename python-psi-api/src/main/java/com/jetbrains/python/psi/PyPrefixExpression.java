// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstPrefixExpression;
import org.jspecify.annotations.Nullable;

public interface PyPrefixExpression extends PyAstPrefixExpression, PyQualifiedExpression, PyReferenceOwner, PyCallSiteExpression {
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getOperand() {
        return (PyExpression)PyAstPrefixExpression.super.getOperand();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getQualifier() {
        return (PyExpression)PyAstPrefixExpression.super.getQualifier();
    }
}
