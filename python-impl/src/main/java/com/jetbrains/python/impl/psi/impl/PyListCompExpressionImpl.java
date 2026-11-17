// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Collections;


public class PyListCompExpressionImpl extends PyComprehensionElementImpl implements PyListCompExpression {
    public PyListCompExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyListCompExpression(this);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression resultExpr = getResultExpression();
        PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
        PyClass list = cache.getClass("list");
        if (resultExpr != null && list != null) {
            PyType elementType = PyLiteralType.upcastLiteralToClass(getResultElementType(resultExpr, context));
            return new PyCollectionTypeImpl(list, false, Collections.singletonList(elementType));
        }
        PyClassType listType = cache.getListType();
        return listType != null ? listType : PyAnyType.getUnknown();
    }
}
