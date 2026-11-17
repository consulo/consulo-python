// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class PyWithItemImpl extends PyElementImpl implements PyWithItem {
    public PyWithItemImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyWithItem(this);
    }

    @Override
    public boolean isSuppressingExceptions(TypeEvalContext context) {
        PyWithStatement withStmt = PsiTreeUtil.getParentOfType(this, PyWithStatement.class, false);
        if (withStmt == null) {
            return false;
        }
        String abstractType = withStmt.isAsync() ? "contextlib.AbstractAsyncContextManager" : "contextlib.AbstractContextManager";
        PyType contextManagerType = PyTypeUtil.convertToType(context.getType(getExpression()), abstractType, this, context);
        PyType exitResultType = null;
        if (contextManagerType instanceof PyClassType classType) {
            List<PyType> typeArguments = classType.getTypeArguments();
            exitResultType = typeArguments.size() > 1 ? typeArguments.get(1) : null;
        }
        return Objects.equals(exitResultType, PyBuiltinCache.getInstance(this).getBoolType()) ||
            exitResultType instanceof PyLiteralType literalType && Boolean.TRUE.equals(literalType.getBoolValue());
    }
}
