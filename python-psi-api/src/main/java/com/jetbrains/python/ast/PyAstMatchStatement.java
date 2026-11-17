// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;
import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByType;

public interface PyAstMatchStatement extends PyAstCompoundStatement {
    @RequiredReadAction
    default @Nullable PyAstExpression getSubject() {
        return findChildByClass(this, PyAstExpression.class);
    }

    @RequiredReadAction
    default List<? extends PyAstCaseClause> getCaseClauses() {
        return findChildrenByType(this, PyElementTypes.CASE_CLAUSE);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyMatchStatement(this);
    }
}
