// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.List;

public interface PyAstConditionalExpression extends PyAstExpression {
    @RequiredReadAction
    default PyAstExpression getTruePart() {
        List<PyAstExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyAstExpression.class);
        return expressions.get(0);
    }

    @RequiredReadAction
    default PyAstExpression getCondition() {
        List<PyAstExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyAstExpression.class);
        return expressions.size() > 1 ? expressions.get(1) : null;
    }

    @RequiredReadAction
    default PyAstExpression getFalsePart() {
        List<PyAstExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyAstExpression.class);
        return expressions.size() == 3 ? expressions.get(2) : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyConditionalExpression(this);
    }
}
