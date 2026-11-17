// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

/**
 * Represents assignment expressions introduced in Python 3.8 (PEP 572).
 */
public interface PyAstAssignmentExpression extends PyAstExpression {
    /**
     * @return LHS of an expression (before :=), null if underlying target is not an identifier.
     */
    @RequiredReadAction
    default @Nullable PyAstTargetExpression getTarget() {
        return getFirstChild() instanceof PyAstTargetExpression target ? target : null;
    }

    /**
     * @return RHS of an expression (after :=), null if assigned value is omitted or not an expression.
     */
    @RequiredReadAction
    default @Nullable PyAstExpression getAssignedValue() {
        return getLastChild() instanceof PyAstExpression expression ? expression : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyAssignmentExpression(this);
    }
}
