// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.util.collection.SmartList;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.List;

public class PyDictCompExpressionImpl extends PyComprehensionElementImpl implements PyDictCompExpression {
    public PyDictCompExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression resultExpr = getResultExpression();
        PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
        PyClassType dictionary = cache.getDictType();
        if (dictionary == null) {
            return PyAnyType.getUnknown();
        }
        // PEP 798: {**d for d in dicts} — key/value types come from the unpacked mapping
        if (resultExpr instanceof PyDoubleStarExpression doubleStarExpression) {
            PyType keyType = null;
            PyType valueType = null;
            PyExpression mapping = doubleStarExpression.getExpression();
            if (mapping != null &&
                    PyTypeUtil.convertToType(context.getType(mapping), "typing.Mapping", doubleStarExpression, context)
                    instanceof PyClassType mappingType) {
                List<PyType> elementTypes = mappingType.getTypeArguments();
                if (elementTypes.size() == 2) {
                    keyType = elementTypes.get(0);
                    valueType = elementTypes.get(1);
                }
            }
            return new PyCollectionTypeImpl(dictionary.getPyClass(), false, new SmartList<>(keyType, valueType));
        }
        if (resultExpr instanceof PyKeyValueExpression keyValue) {
            PyType keyType = PyLiteralType.upcastLiteralToClass(keyValue.getKey().getType(context));
            PyExpression value = keyValue.getValue();
            PyType valueType = value != null ? PyLiteralType.upcastLiteralToClass(value.getType(context)) : null;
            return new PyCollectionTypeImpl(dictionary.getPyClass(), false, new SmartList<>(keyType, valueType));
        }
        return dictionary;
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyDictCompExpression(this);
    }
}
