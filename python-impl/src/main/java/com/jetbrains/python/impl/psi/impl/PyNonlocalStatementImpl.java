// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.ast.TokenSet;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyTargetExpression;

import java.util.Arrays;
import java.util.List;


// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PyNonlocalStatementImpl extends PyElementImpl implements PyNonlocalStatement {
    private static final TokenSet TARGET_EXPRESSION_SET = TokenSet.create(PyElementTypes.TARGET_EXPRESSION);

    public PyNonlocalStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public void acceptPyVisitor(PyElementVisitor visitor) {
        visitor.visitPyNonlocalStatement(this);
    }

    @Override
    public PyTargetExpression[] getVariables() {
        return childrenToPsi(TARGET_EXPRESSION_SET, PyTargetExpression.EMPTY_ARRAY);
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        if (ArrayUtil.contains(child.getPsi(), getVariables())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
        }
        super.deleteChildInternal(child);
    }

    public List<? extends PsiElement> getComponents() {
        return Arrays.asList(getVariables());
    }
}
