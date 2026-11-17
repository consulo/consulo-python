// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public interface PyAstKeyValueExpression extends PyAstExpression {
    PyAstKeyValueExpression[] EMPTY_ARRAY = new PyAstKeyValueExpression[0];

    @RequiredReadAction
    default PyAstExpression getKey() {
        return (PyAstExpression)getNode().getFirstChildNode().getPsi();
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getValue() {
        return PsiTreeUtil.getNextSiblingOfType(getKey(), PyAstExpression.class);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyKeyValueExpression(this);
    }
}
