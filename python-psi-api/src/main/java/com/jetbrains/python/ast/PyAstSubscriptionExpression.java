// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.AccessDirection;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface PyAstSubscriptionExpression extends PyAstQualifiedExpression, PyAstCallSiteExpression, PyAstReferenceOwner {

    @RequiredReadAction
    default List<? extends PyAstExpression> getArguments() {
        List<PyAstExpression> result = new ArrayList<>();
        ContainerUtil.addIfNotNull(result, getIndexExpression());
        if (AccessDirection.of(this) == AccessDirection.WRITE && getParent() instanceof PyAstAssignmentStatement assignment) {
            ContainerUtil.addIfNotNull(result, assignment.getAssignedValue());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
     */
    @RequiredReadAction
    default PyAstExpression getRootOperand() {
        PyAstExpression operand = getOperand();
        while (operand instanceof PyAstSubscriptionExpression) {
            operand = ((PyAstSubscriptionExpression)operand).getOperand();
        }
        return operand;
    }

    @RequiredReadAction
    default PyAstExpression getOperand() {
        return childToPsiNotNull(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getIndexExpression() {
        return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
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
        return switch (AccessDirection.of(this)) {
            case READ -> PyNames.GETITEM;
            case WRITE -> PyNames.SETITEM;
            case DELETE -> PyNames.DELITEM;
        };
    }

    @Override
    @RequiredReadAction
    default ASTNode getNameElement() {
        return getNode().findChildByType(PyTokenTypes.LBRACKET);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPySubscriptionExpression(this);
    }
}
