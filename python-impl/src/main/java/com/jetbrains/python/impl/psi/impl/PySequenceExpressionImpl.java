// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;

import java.util.Arrays;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public abstract class PySequenceExpressionImpl extends PyElementImpl implements PySequenceExpression {
    public PySequenceExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        if (ArrayUtil.contains(child.getPsi(), getElements())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
        }
        super.deleteChildInternal(child);
    }

    @Override
    public PyExpression[] getElements() {
        return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyExpression.EMPTY_ARRAY);
    }

    public List<? extends PsiElement> getComponents() {
        return Arrays.asList(getElements());
    }
}
