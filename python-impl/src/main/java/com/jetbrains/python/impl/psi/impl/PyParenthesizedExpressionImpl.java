// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;


public class PyParenthesizedExpressionImpl extends PyElementImpl implements PyParenthesizedExpression {
    public PyParenthesizedExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyParenthesizedExpression(this);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression expr = getContainedExpression();
        return expr != null ? context.getType(expr) : PyAnyType.getUnknown();
    }
}
