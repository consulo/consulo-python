// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.impl.psi.impl.references.PyImportReference;
import com.jetbrains.python.impl.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;

/**
 *
 * User : ktisha
 */
public class PyDoctestReferenceExpression extends PyReferenceExpressionImpl {

    public PyDoctestReferenceExpression(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PsiPolyVariantReference getReference(PyResolveContext context) {
        if (isQualified()) {
            return new PyQualifiedReference(this, context);
        }
        final PsiElement importParent = PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class);
        if (importParent != null) {
            return PyImportReference.forElement(this, importParent, context);
        }
        return new PyDoctestReference(this, context);
    }
}

