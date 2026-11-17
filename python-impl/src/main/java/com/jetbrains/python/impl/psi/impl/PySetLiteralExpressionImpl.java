// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;


public class PySetLiteralExpressionImpl extends PySequenceExpressionImpl implements PySetLiteralExpression {
    public PySetLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        return PyCollectionTypeUtil.getSetLiteralType(this, context);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPySetLiteralExpression(this);
    }
}
