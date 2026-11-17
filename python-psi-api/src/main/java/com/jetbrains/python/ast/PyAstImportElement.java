// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByType;

public interface PyAstImportElement extends PyAstElement, PyAstImportedNameDefiner {
    @Override
    @RequiredReadAction
    default @Nullable String getName() {
        return getVisibleName();
    }

    @RequiredReadAction
    default @Nullable PyAstReferenceExpression getImportReferenceExpression() {
        ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens());
        return node == null ? null : (PyAstReferenceExpression) node.getPsi();
    }

    @RequiredReadAction
    default @Nullable QualifiedName getImportedQName() {
        PyAstReferenceExpression importReference = getImportReferenceExpression();
        return importReference != null ? importReference.asQualifiedName() : null;
    }

    @RequiredReadAction
    default @Nullable PyAstTargetExpression getAsNameElement() {
        return findChildByType(this, PyElementTypes.TARGET_EXPRESSION);
    }

    @RequiredReadAction
    default @Nullable String getAsName() {
        PyAstTargetExpression element = getAsNameElement();
        return element != null ? element.getName() : null;
    }

    /**
     * @return name under which the element is visible, that is, "as name" is there is one, or just name.
     */
    @RequiredReadAction
    default @Nullable String getVisibleName() {
        PyAstTargetExpression asNameElement = getAsNameElement();
        if (asNameElement != null) {
            return asNameElement.getName();
        }
        QualifiedName importedName = getImportedQName();
        if (importedName != null && importedName.getComponentCount() > 0) {
            return importedName.getComponents().get(0);
        }
        return null;
    }

    @RequiredReadAction
    default PyAstStatement getContainingImportStatement() {
        PsiElement parent = getParent();
        return parent instanceof PyAstStatement ? (PyAstStatement)parent : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyImportElement(this);
    }
}
