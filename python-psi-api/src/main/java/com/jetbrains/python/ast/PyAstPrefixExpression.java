// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.PyElementType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

public interface PyAstPrefixExpression extends PyAstQualifiedExpression, PyAstReferenceOwner, PyAstCallSiteExpression {
    @RequiredReadAction
    default @Nullable PyAstExpression getOperand() {
        return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
    }

    @RequiredReadAction
    default @Nullable PsiElement getPsiOperator() {
        ASTNode node = getNode();
        ASTNode child = node.findChildByType(PyTokenTypes.UNARY_OPS);
        return child != null ? child.getPsi() : null;
    }

    @RequiredReadAction
    default PyElementType getOperator() {
        PsiElement op = getPsiOperator();
        assert op != null;
        return (PyElementType)op.getNode().getElementType();
    }

    @Override
    @RequiredReadAction
    default PyAstExpression getQualifier() {
        return getOperand();
    }

    @Override
    @RequiredReadAction
    default @Nullable QualifiedName asQualifiedName() {
        return PyPsiUtilsCore.asQualifiedName(this);
    }

    @Override
    @RequiredReadAction
    default boolean isQualified() {
        return getQualifier() != null;
    }

    @Override
    @RequiredReadAction
    default String getReferencedName() {
        PyElementType t = getOperator();
        if (t == PyTokenTypes.PLUS) {
            return PyNames.POS;
        }
        else if (t == PyTokenTypes.MINUS) {
            return PyNames.NEG;
        }
        return getOperator().getSpecialMethodName();
    }

    @Override
    @RequiredReadAction
    default ASTNode getNameElement() {
        PsiElement op = getPsiOperator();
        return op != null ? op.getNode() : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyPrefixExpression(this);
    }
}
