// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

public class PyLiteralPatternImpl extends PyElementImpl implements PyLiteralPattern {
    public PyLiteralPatternImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyLiteralPattern(this);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyType literalType = PyLiteralType.fromLiteralParameter(getExpression(), context, false);
        if (literalType != null) {
            return literalType;
        }
        return context.getType(getExpression());
    }
}
