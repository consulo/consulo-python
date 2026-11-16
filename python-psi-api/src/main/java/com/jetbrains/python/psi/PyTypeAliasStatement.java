// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.psi;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Represents Type Alias Statement added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 * <p>
 * Consulo: the default methods of JB {@code com.jetbrains.python.ast.PyAstTypeAliasStatement} are declared here,
 * because the python-ast layer is not ported.
 */
public interface PyTypeAliasStatement
    extends PyStatement, PsiNameIdentifierOwner, PyTypeParameterListOwner, PyTypedElement,
    StubBasedPsiElement<PyTypeAliasStatementStub>, PyQualifiedNameOwner, ScopeOwner {

    @Override
    default @Nullable PsiElement getNameIdentifier() {
        ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
        return nameNode != null ? nameNode.getPsi() : null;
    }

    default @Nullable PyExpression getTypeExpression() {
        PsiElement child = getLastChild();
        while (child != null && !(child instanceof PyExpression)) {
            if (child instanceof PsiErrorElement) {
                return null;
            }
            child = child.getPrevSibling();
        }
        return (PyExpression) child;
    }

    /**
     * Returns right-hand side of the type alias statement as text.
     * <p>
     * The text is taken from stub if the stub is presented.
     */
    @Nullable
    String getTypeExpressionText();
}
