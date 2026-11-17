// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Represents a Type Parameter that can be a part of {@link PyAstTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyAstTypeParameter extends PyAstElement, PsiNameIdentifierOwner, PyAstTypedElement {

    enum Kind {
        TypeVar(0),
        TypeVarTuple(1),
        ParamSpec(2);

        private final int myIndex;

        Kind(int index) {
            myIndex = index;
        }

        public int getIndex() {
            return myIndex;
        }

        public static Kind fromIndex(int index) {
            return switch (index) {
                case 1 -> TypeVarTuple;
                case 2 -> ParamSpec;
                default -> TypeVar;
            };
        }
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getBoundExpression() {
        PsiElement element = Arrays.stream(getChildren())
            .filter(child -> {
                PsiElement e = PsiTreeUtil.skipWhitespacesBackward(child);
                return e != null && e.getNode().getElementType() == PyTokenTypes.COLON;
            })
            .findFirst()
            .orElse(null);
        if (element instanceof PyAstExpression expression) {
            return expression;
        }
        return null;
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getDefaultExpression() {
        PsiElement element = Arrays.stream(getChildren())
            .filter(child -> {
                PsiElement e = PsiTreeUtil.skipWhitespacesBackward(child);
                return e != null && e.getNode().getElementType() == PyTokenTypes.EQ;
            })
            .findFirst()
            .orElse(null);
        if (element instanceof PyAstExpression expression) {
            return expression;
        }
        return null;
    }

    /**
     * Returns the text of the bound expression of the type parameter
     * <p>
     * The text is taken from stub if the stub is presented.
     */
    @RequiredReadAction
    default @Nullable String getBoundExpressionText() {
        PyAstExpression boundExpression = getBoundExpression();
        if (boundExpression != null) {
            return boundExpression.getText();
        }

        return null;
    }

    @RequiredReadAction
    default @Nullable String getDefaultExpressionText() {
        PyAstExpression defaultExpression = getDefaultExpression();
        if (defaultExpression != null) {
            return defaultExpression.getText();
        }

        return null;
    }

    @RequiredReadAction
    default Kind getKind() {
        String paramText = getText();
        if (paramText.startsWith("**")) {
            return Kind.ParamSpec;
        }
        else if (paramText.startsWith("*")) {
            return Kind.TypeVarTuple;
        }
        else {
            return Kind.TypeVar;
        }
    }

    @Override
    @RequiredReadAction
    default @Nullable String getName() {
        PsiElement identifier = getNameIdentifier();
        return identifier != null ? identifier.getText() : null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PsiElement getNameIdentifier() {
        ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
        return nameNode != null ? nameNode.getPsi() : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyTypeParameter(this);
    }
}
