/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyUtilCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiNamedElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface PyAstExceptPart extends PyAstElement, PyAstNamedElementContainer, PyAstStatementPart {
    PyAstExceptPart[] EMPTY_ARRAY = new PyAstExceptPart[0];

    @RequiredReadAction
    default @Nullable PyAstExpression getExceptClass() {
        return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
    }

    @RequiredReadAction
    default @Nullable PyAstExpression getTarget() {
        return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
    }

    @RequiredReadAction
    default boolean isStar() {
        return getNode().findChildByType(PyTokenTypes.MULT) != null;
    }

    @Override
    @RequiredReadAction
    default List<PsiNamedElement> getNamedElements() {
        List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndStars(getTarget());
        List<PsiNamedElement> results = new ArrayList<>();
        for (PyAstExpression expression : expressions) {
            if (expression instanceof PsiNamedElement) {
                results.add((PsiNamedElement)expression);
            }
        }
        return results;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyExceptBlock(this);
    }
}
