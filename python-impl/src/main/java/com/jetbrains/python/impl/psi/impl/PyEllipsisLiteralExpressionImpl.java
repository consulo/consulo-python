// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyEllipsisLiteralExpressionImpl extends PyElementImpl implements PyEllipsisLiteralExpression, PyInstantTypeProvider {
    public PyEllipsisLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyType ellipsisType = PyBuiltinCache.getInstance(this).getEllipsisType();
        return ellipsisType != null ? ellipsisType : PyAnyType.getUnknown();
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyEllipsisLiteralExpression(this);
    }
}
