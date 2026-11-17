// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.PyElementType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

public interface PyAstAugAssignmentStatement extends PyAstStatement, PyAstQualifiedExpression, PyAstReferenceOwner {
    @RequiredReadAction
    default PyAstExpression getTarget() {
        PyAstExpression target = childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
        if (target == null) {
            throw new RuntimeException("Target missing in augmented assignment statement");
        }
        return target;
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getValue() {
        return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
    }

    @RequiredReadAction
    default @Nullable PsiElement getOperation() {
        return PyPsiUtilsCore.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0);
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstExpression getQualifier() {
        return getTarget();
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
    default @Nullable String getReferencedName() {
        PsiElement operation = getOperation();
        PyElementType t = (PyElementType)(operation != null ? operation.getNode().getElementType() : null);
        return t.getSpecialMethodName();
    }

    @Override
    @RequiredReadAction
    default @Nullable ASTNode getNameElement() {
        PsiElement operation = getOperation();
        return operation != null ? operation.getNode() : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyAugAssignmentStatement(this);
    }
}
