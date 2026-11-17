// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.PyElementType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

public interface PyAstBinaryExpression extends PyAstQualifiedExpression, PyAstCallSiteExpression, PyAstReferenceOwner {

    @RequiredReadAction
    default PyAstExpression getLeftExpression() {
        return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getRightExpression() {
        return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyAstExpression.class);
    }

    @RequiredReadAction
    default @Nullable PyElementType getOperator() {
        PsiElement psiOperator = getPsiOperator();
        return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
    }

    @RequiredReadAction
    default @Nullable PsiElement getPsiOperator() {
        ASTNode node = getNode();
        ASTNode child = node.findChildByType(PyTokenTypes.BINARY_OPS);
        if (child != null) {
            return child.getPsi();
        }
        return null;
    }

    @RequiredReadAction
    default boolean isShortCircuit() {
        PyElementType operator = getOperator();
        return operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD;
    }

    @RequiredReadAction
    default boolean isOperator(String chars) {
        return getOperatorTokensText().equals(chars);
    }

    /**
     * The concatenated text of the {@link PyTokenTypes#BINARY_OPS} tokens making up this expression's operator,
     * e.g. {@code "notin"} for {@code not in} - a compound operator spread across two sibling keyword tokens.
     */
    @RequiredReadAction
    default String getOperatorTokensText() {
        ASTNode child = getNode().getFirstChildNode();
        StringBuilder buf = new StringBuilder();
        while (child != null) {
            IElementType elType = child.getElementType();
            if (elType instanceof PyElementType && PyTokenTypes.BINARY_OPS.contains(elType)) {
                buf.append(child.getText());
            }
            child = child.getTreeNext();
        }
        return buf.toString();
    }

    @RequiredReadAction
    default boolean isRightOperator(@Nullable PyAstCallable resolvedCallee) {
        return resolvedCallee != null && PyNames.isRightOperatorName(getReferencedName(), resolvedCallee.getName());
    }

    @Override
    @RequiredReadAction
    default PyAstExpression getQualifier() {
        return getLeftExpression();
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
        if (t == PyTokenTypes.DIV && isTrueDivEnabled(this)) {
            return PyNames.TRUEDIV;
        }
        if (t == PyTokenTypes.NOT_KEYWORD && isOperator("notin")) {
            return PyNames.CONTAINS;
        }
        return t != null ? t.getSpecialMethodName() : null;
    }

    @Override
    @RequiredReadAction
    default ASTNode getNameElement() {
        PsiElement op = getPsiOperator();
        return op != null ? op.getNode() : null;
    }

    static @Nullable PyAstExpression getChainedComparisonAwareLeftExpression(PyAstBinaryExpression expression) {
        PyAstExpression leftOperand = expression.getLeftExpression();
        if (PyTokenTypes.COMPARISON_OPERATIONS.contains(expression.getOperator())) {
            PyAstBinaryExpression leftBinaryExpr = ObjectUtil.tryCast(leftOperand, PyAstBinaryExpression.class);
            if (leftBinaryExpr != null && PyTokenTypes.COMPARISON_OPERATIONS.contains(leftBinaryExpr.getOperator())) {
                return leftBinaryExpr.getRightExpression();
            }
        }
        return leftOperand;
    }

    private static boolean isTrueDivEnabled(PyAstElement anchor) {
        PsiFile file = anchor.getContainingFile();
        if (file instanceof PyAstFile pyFile) {
            return FutureFeature.DIVISION.requiredAt(pyFile.getLanguageLevel()) || pyFile.hasImportFromFuture(FutureFeature.DIVISION);
        }
        return false;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyBinaryExpression(this);
    }
}
