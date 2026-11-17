// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PyCallExpressionImpl extends PyElementImpl implements PyCallExpression {

    public PyCallExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyCallExpression(this);
    }

    @Override
    public List<PyCallableType> multiResolveCallee(PyResolveContext resolveContext) {
        return PyCallExpressionHelper.multiResolveCallee(this, resolveContext);
    }

    @Override
    public List<PyArgumentsMapping> multiMapArguments(PyResolveContext resolveContext) {
        return PyCallExpressionHelper.mapArguments(this, resolveContext);
    }

    @Override
    public String toString() {
        return "PyCallExpression: " + PyUtil.getReadableRepr(getCallee(), true); //or: getCalledFunctionReference().getReferencedName();
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        return PyCallExpressionHelper.getCallType(this, context, key);
    }
}
