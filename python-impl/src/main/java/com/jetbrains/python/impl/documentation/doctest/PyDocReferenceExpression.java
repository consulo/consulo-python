/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.impl.documentation.doctest;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.impl.psi.impl.references.PyImportReference;
import com.jetbrains.python.impl.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import jakarta.annotation.Nonnull;

/**
 *
 * User : ktisha
 */
public class PyDocReferenceExpression extends PyReferenceExpressionImpl {

  public PyDocReferenceExpression(ASTNode astNode) {
    super(astNode);
  }

  @Nonnull
  public PsiPolyVariantReference getReference(PyResolveContext context) {
    final PyExpression qualifier = getQualifier();
    if (qualifier != null) {
      return new PyQualifiedReference(this, context);
    }
    final PsiElement importParent = PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class);
    if (importParent != null) {
      return PyImportReference.forElement(this, importParent, context);
    }
    return new PyDocReference(this, context);
  }
}

