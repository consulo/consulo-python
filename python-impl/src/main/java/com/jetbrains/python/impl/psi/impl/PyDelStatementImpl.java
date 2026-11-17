// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;

import java.util.Arrays;
import java.util.List;

public class PyDelStatementImpl extends PyElementImpl implements PyDelStatement /* TODO consulo: PsiListLikeElement missing */ {
    public PyDelStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyDelStatement(this);
    }

    @Override
    public PyExpression[] getTargets() {
        return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyExpression.EMPTY_ARRAY);
    }

    public List<? extends PsiElement> getComponents() {
        return Arrays.asList(getTargets());
    }
}
