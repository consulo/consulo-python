// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public interface PyAstStarArgument extends PyAstExpression {

    /**
     * Returns the expression being unpacked by this star argument.
     * <p>
     * For {@code *args}, returns the expression {@code args}.
     * For {@code **kwargs}, returns the expression {@code kwargs}.
     *
     * @return the inner expression, or {@code null} if the argument is incomplete
     */
    @RequiredReadAction
    default @Nullable PyAstExpression getExpression() {
        return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
    }

    @RequiredReadAction
    default boolean isKeyword() {
        return getNode().findChildByType(PyTokenTypes.EXP) != null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor visitor) {
        visitor.visitPyStarArgument(this);
    }
}
