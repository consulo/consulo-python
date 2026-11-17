// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references.hasattr;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;

import java.util.HashSet;
import java.util.Set;

public class PyHasAttrVisitor extends PyRecursiveElementVisitor {
    private final PsiElement resolvedQualifier;
    private final Set<String> result = new HashSet<>();
    private boolean myPositive = true;

    public PyHasAttrVisitor(PsiElement resolvedQualifier) {
        this.resolvedQualifier = resolvedQualifier;
    }

    public Set<String> getResult() {
        return result;
    }

    @Override
    public void visitPyPrefixExpression(PyPrefixExpression node) {
        if (node.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            myPositive = !myPositive;
            super.visitPyPrefixExpression(node);
            myPositive = !myPositive;
        }
        else {
            super.visitPyPrefixExpression(node);
        }
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
        if (!node.isOperator(PyNames.AND) && !node.isOperator(PyNames.OR)) {
            return;
        }
        super.visitPyBinaryExpression(node);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
        if (!myPositive) {
            return;
        }
        if (!node.isCalleeText(PyNames.HAS_ATTR)) {
            return;
        }
        if (node.getArguments().length != 2) {
            return;
        }
        PyReferenceExpression firstArg = node.getArgument(0, PyReferenceExpression.class);
        if (firstArg == null) {
            return;
        }
        PyStringLiteralExpression attrName = node.getArgument(1, PyStringLiteralExpression.class);
        if (attrName == null) {
            return;
        }

        if (firstArg.getReference().isReferenceTo(resolvedQualifier)) {
            String variant = attrName.getStringValue();
            if (StringUtil.isJavaIdentifier(variant)) {
                result.add(variant);
            }
        }
    }
}
