// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

public interface PyAstCaseClause extends PyAstStatementPart {
    @RequiredReadAction
    default @Nullable PyAstPattern getPattern() {
        return findChildByClass(this, PyAstPattern.class);
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getGuardCondition() {
        return findChildByClass(this, PyAstExpression.class);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyCaseClause(this);
    }
}
