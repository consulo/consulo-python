// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyConditionalExpressionImpl extends PyElementImpl implements PyConditionalExpression {
    public PyConditionalExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression truePart = getTruePart();
        PyExpression falsePart = getFalsePart();
        if (truePart == null || falsePart == null) {
            return PyAnyType.getUnknown();
        }
        return PyUnionType.unionOrUnknown(context.getType(truePart), context.getType(falsePart));
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyConditionalExpression(this);
    }
}
