// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public interface PyAstLambdaExpression extends PyAstExpression, PyAstCallable, AstScopeOwner {
    @Override
    @RequiredReadAction
    default PyAstParameterList getParameterList() {
        PyAstElement child = childToPsi(PyElementTypes.PARAMETER_LIST_SET, 0);
        if (child == null) {
            throw new RuntimeException("parameter list must not be null; text=" + getText());
        }
        return (PyAstParameterList)child;
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getBody() {
        return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
    }

    @Override
    default @Nullable PyAstFunction asMethod() {
        return null; // we're never a method
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyLambdaExpression(this);
    }
}
