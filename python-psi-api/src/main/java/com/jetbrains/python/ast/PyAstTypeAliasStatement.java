// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import org.jspecify.annotations.Nullable;

/**
 * Represents Type Alias Statement added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyAstTypeAliasStatement extends PyAstStatement, PsiNameIdentifierOwner, PyAstTypeParameterListOwner, PyAstTypedElement,
                                                 PyAstQualifiedNameOwner, AstScopeOwner {

    @Override
    @RequiredReadAction
    default @Nullable PsiElement getNameIdentifier() {
        ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
        return nameNode != null ? nameNode.getPsi() : null;
    }

    @Override
    @Nullable PyAstTypeParameterList getTypeParameterList();

    @RequiredReadAction
    default @Nullable PyAstExpression getTypeExpression() {
        PsiElement child = getLastChild();
        while (child != null && !(child instanceof PyAstExpression)) {
            if (child instanceof PsiErrorElement) {
                return null;
            }
            child = child.getPrevSibling();
        }
        return (PyAstExpression)child;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyTypeAliasStatement(this);
    }
}
