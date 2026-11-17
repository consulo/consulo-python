// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyBoolLiteralExpressionImpl extends PyElementImpl implements PyBoolLiteralExpression, PyInstantTypeProvider {
    public PyBoolLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        // `__debug__` depends on the `-O` flag, so it has no literal value.
        PyType type = PyLiteralType.inferLiteralTypeForLiteralExpressions() && !PyNames.DEBUG.equals(getText())
            ? PyLiteralType.boolLiteral(this, getValue())
            : PyBuiltinCache.getInstance(this).getBoolType();
        // Both give null only when the project has no builtins.
        return type != null ? type : PyAnyType.getUnknown();
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyBoolLiteralExpression(this);
    }
}
