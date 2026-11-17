// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiPolyVariantReference;
import org.jspecify.annotations.Nullable;

public class PyAugAssignmentStatementImpl extends PyElementImpl implements PyAugAssignmentStatement {
    public PyAugAssignmentStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyAugAssignmentStatement(this);
    }

    /**
     * the type after the operation has been applied
     */
    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        return PyCallExpressionHelper.getCallType(this, context, key);
    }

    @Override
    public PsiPolyVariantReference getReference() {
        return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
    }

    @Override
    public PsiPolyVariantReference getReference(PyResolveContext context) {
        return new PyOperatorReference(this, context);
    }
}
