// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;


public class PyKeywordArgumentImpl extends PyElementImpl implements PyKeywordArgument {
    public PyKeywordArgumentImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + getKeyword();
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression e = getValueExpression();
        return e != null ? context.getType(e) : PyAnyType.getUnknown();
    }

    @Override
    public PsiReference getReference() {
        ASTNode keywordNode = getKeywordNode();
        if (keywordNode != null) {
            return new PyKeywordArgumentReference(this, keywordNode.getPsi().getTextRangeInParent());
        }
        return null;
    }

    @Override
    public String getName() {
        return PyKeywordArgument.super.getName();
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
        PyExpression expression = getValueExpression();
        PyKeywordArgument keywordArgument = generator.createKeywordArgument(LanguageLevel.forElement(this), name,
                                                                            expression != null ? expression.getText() : name);
        getNode().replaceChild(getKeywordNode(), keywordArgument.getKeywordNode());
        return this;
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyKeywordArgument(this);
    }
}
