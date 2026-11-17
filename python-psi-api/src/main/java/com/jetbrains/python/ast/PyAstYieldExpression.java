// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public interface PyAstYieldExpression extends PyAstExpression {
    @RequiredReadAction
    default @Nullable PyAstExpression getExpression() {
        PyAstExpression[] expressions = PsiTreeUtil.getChildrenOfType(this, PyAstExpression.class);
        return (expressions != null && expressions.length > 0) ? expressions[0] : null;
    }

    @RequiredReadAction
    default boolean isDelegating() {
        return getNode().findChildByType(PyTokenTypes.FROM_KEYWORD) != null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyYieldExpression(this);
    }
}
