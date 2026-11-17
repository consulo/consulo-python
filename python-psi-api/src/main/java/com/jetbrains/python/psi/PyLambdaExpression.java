// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstLambdaExpression;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import org.jspecify.annotations.Nullable;

public interface PyLambdaExpression extends PyAstLambdaExpression, PyExpression, PyCallable, ScopeOwner {
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getBody() {
        return (PyExpression)PyAstLambdaExpression.super.getBody();
    }

    @Override
    @RequiredReadAction
    default PyParameterList getParameterList() {
        return (PyParameterList)PyAstLambdaExpression.super.getParameterList();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyFunction asMethod() {
        return (PyFunction)PyAstLambdaExpression.super.asMethod();
    }
}
