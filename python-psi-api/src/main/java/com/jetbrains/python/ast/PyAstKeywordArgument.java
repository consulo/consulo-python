// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public interface PyAstKeywordArgument extends PyAstExpression, PsiNamedElement {
    @RequiredReadAction
    default @Nullable String getKeyword() {
        ASTNode node = getKeywordNode();
        return node != null ? node.getText() : null;
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getValueExpression() {
        return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
    }

    @RequiredReadAction
    default @Nullable ASTNode getKeywordNode() {
        return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    }

    @Override
    @RequiredReadAction
    default @Nullable String getName() {
        return getKeyword();
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyKeywordArgument(this);
    }
}
