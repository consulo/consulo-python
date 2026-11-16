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
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Represents a Type Parameter that can be a part of {@link PyTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 * <p>
 * Consulo: the {@link Kind} enum and the default methods of JB {@code com.jetbrains.python.ast.PyAstTypeParameter}
 * are declared here, because the python-ast layer is not ported.
 */
public interface PyTypeParameter extends PyElement, PsiNameIdentifierOwner, PyTypedElement, PyQualifiedNameOwner,
    StubBasedPsiElement<PyTypeParameterStub> {

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

    default @Nullable PyExpression getBoundExpression() {
        PsiElement element = Arrays.stream(getChildren())
            .filter(child -> {
                PsiElement e = PsiTreeUtil.skipWhitespacesBackward(child);
                return e != null && e.getNode().getElementType() == PyTokenTypes.COLON;
            })
            .findFirst()
            .orElse(null);
        if (element instanceof PyExpression expression) {
            return expression;
        }
        return null;
    }

    default @Nullable PyExpression getDefaultExpression() {
        PsiElement element = Arrays.stream(getChildren())
            .filter(child -> {
                PsiElement e = PsiTreeUtil.skipWhitespacesBackward(child);
                return e != null && e.getNode().getElementType() == PyTokenTypes.EQ;
            })
            .findFirst()
            .orElse(null);
        if (element instanceof PyExpression expression) {
            return expression;
        }
        return null;
    }

    /**
     * Returns the text of the bound expression of the type parameter
     * <p>
     * The text is taken from stub if the stub is presented.
     */
    default @Nullable String getBoundExpressionText() {
        PyExpression boundExpression = getBoundExpression();
        if (boundExpression != null) {
            return boundExpression.getText();
        }

        return null;
    }

    default @Nullable String getDefaultExpressionText() {
        PyExpression defaultExpression = getDefaultExpression();
        if (defaultExpression != null) {
            return defaultExpression.getText();
        }

        return null;
    }

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
    default @Nullable String getName() {
        PsiElement identifier = getNameIdentifier();
        return identifier != null ? identifier.getText() : null;
    }

    @Override
    default @Nullable PsiElement getNameIdentifier() {
        ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
        return nameNode != null ? nameNode.getPsi() : null;
    }
}
