// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PyDecoratorImpl extends PyBaseElementImpl<PyDecoratorStub> implements PyDecorator {

    public PyDecoratorImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyDecoratorImpl(PyDecoratorStub stub) {
        super(stub, PyStubElementTypes.DECORATOR_CALL);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyDecorator(this);
    }

    /**
     * @return the name of decorator, without the "@". Stub is used if available.
     */
    @Override
    public String getName() {
        return PyDecorator.super.getName();
    }

    @Override
    public boolean isBuiltin() {
        ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens());
        if (node != null) {
            PyReferenceExpression ref = (PyReferenceExpression)node.getPsi();
            PsiElement target = ref.getReference().resolve();
            return PyBuiltinCache.getInstance(this).isBuiltin(target);
        }
        return false;
    }

    @Override
    public boolean hasArgumentList() {
        PyDecoratorStub stub = getStub();
        if (stub != null) {
            return stub.hasArgumentList();
        }
        else {
            return getExpression() instanceof PyCallExpression;
        }
    }

    @Override
    public @Nullable QualifiedName getQualifiedName() {
        PyDecoratorStub stub = getStub();
        if (stub != null) {
            return stub.getQualifiedName();
        }
        else {
            return PyDecorator.super.getQualifiedName();
        }
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
        return "PyDecorator: @" + getQualifiedName();
    }

    public PsiElement setName(String name) throws IncorrectOperationException {
        ASTNode node = getNode();
        ASTNode nameNode = node.findChildByType(PyTokenTypes.IDENTIFIER);
        if (nameNode != null) {
            ASTNode nameElement = PyUtil.createNewName(this, name);
            node.replaceChild(nameNode, nameElement);
            return this;
        }
        else {
            throw new IncorrectOperationException("No name node");
        }
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        return PyCallExpressionHelper.getCallType(this, context, key);
    }
}
