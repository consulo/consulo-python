// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.ast.ASTNode;
import consulo.python.impl.localize.PyLocalize;


final class PyGeneratorInArgumentListAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyGeneratorInArgumentListAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyArgumentList(PyArgumentList node) {
        if (node.getArguments().length > 1) {
            for (PyExpression expression : node.getArguments()) {
                if (expression instanceof PyGeneratorExpression) {
                    ASTNode firstChildNode = expression.getNode().getFirstChildNode();
                    if (firstChildNode.getElementType() != PyTokenTypes.LPAR) {
                        myHolder.markError(expression, PyLocalize.annGeneratorExpressionMustBeParenthesizedIfNotSoleArgument().get());
                    }
                }
            }
        }
    }
}
