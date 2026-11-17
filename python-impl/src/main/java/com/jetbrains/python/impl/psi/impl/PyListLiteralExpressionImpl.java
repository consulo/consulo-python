// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyListLiteralExpressionImpl extends PySequenceExpressionImpl implements PyListLiteralExpression {
    public PyListLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyListLiteralExpression(this);
    }

    @Override
    public PsiElement add(PsiElement psiElement) throws IncorrectOperationException {
        checkPyExpression(psiElement);
        PyExpression element = (PyExpression)psiElement;
        PyExpression[] els = getElements();
        PyExpression lastArg = els.length == 0 ? null : els[els.length - 1];
        return PyElementGenerator.getInstance(getProject()).insertItemIntoListRemoveRedundantCommas(this, lastArg, element);
    }

    private static void checkPyExpression(PsiElement psiElement) throws IncorrectOperationException {
        if (!(psiElement instanceof PyExpression)) {
            throw new IncorrectOperationException("Element must be PyExpression: " + psiElement);
        }
    }

    @Override
    public PsiElement addAfter(PsiElement psiElement, PsiElement afterThis) throws IncorrectOperationException {
        checkPyExpression(afterThis);
        if (psiElement.getNode().getElementType() == PyTokenTypes.COMMA) {
            return super.addAfter(psiElement, afterThis);
        }
        checkPyExpression(psiElement);
        return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, (PyExpression)afterThis, (PyExpression)psiElement);
    }

    @Override
    public PsiElement addBefore(PsiElement psiElement, PsiElement beforeThis) throws IncorrectOperationException {
        checkPyExpression(psiElement);
        return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, null, (PyExpression)psiElement);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        return PyCollectionTypeUtil.getListLiteralType(this, context);
    }
}
