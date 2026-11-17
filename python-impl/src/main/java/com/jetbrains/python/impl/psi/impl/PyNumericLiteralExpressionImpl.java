// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;


public class PyNumericLiteralExpressionImpl extends PyElementImpl implements PyNumericLiteralExpression, PyInstantTypeProvider {

    public PyNumericLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyNumericLiteralExpression(this);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        if (isIntegerLiteral()) {
            PyType result = PyLiteralType.inferLiteralTypeForLiteralExpressions()
                         ? PyLiteralType.intLiteral(this, getBigIntegerValue())
                         : PyBuiltinCache.getInstance(this).getIntType();
            return result == null ? PyAnyType.getUnknown() : result;
        }

        IElementType type = getNode().getElementType();
        PyClassType result = null;
        if (type == PyElementTypes.FLOAT_LITERAL_EXPRESSION) {
            result = PyBuiltinCache.getInstance(this).getFloatType();
        }
        else if (type == PyElementTypes.IMAGINARY_LITERAL_EXPRESSION) {
            result = PyBuiltinCache.getInstance(this).getComplexType();
        }
        return result == null ? PyAnyType.getUnknown() : result;
    }
}
