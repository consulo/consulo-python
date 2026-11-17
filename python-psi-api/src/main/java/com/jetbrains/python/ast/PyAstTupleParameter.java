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

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

/**
 * Tuple parameter. Defines nothing; this interface is only needed for stub creation.
 */
public interface PyAstTupleParameter extends PyAstParameter {

    @Override
    default @Nullable PyAstNamedParameter getAsNamed() {
        return null;  // we're not named
    }

    @Override
    default PyAstTupleParameter getAsTuple() {
        return this;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstExpression getDefaultValue() {
        ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
        if (nodes.length > 0) {
            return (PyAstExpression)nodes[0].getPsi();
        }
        return null;
    }

    @Override
    @RequiredReadAction
    default boolean hasDefaultValue() {
        return getDefaultValue() != null;
    }

    @Override
    @RequiredReadAction
    default @Nullable String getDefaultValueText() {
        return ParamHelperCore.getDefaultValueText(getDefaultValue());
    }

    @Override
    default boolean isSelf() {
        return false;
    }

    /**
     * @return the nested parameters within this tuple parameter.
     */
    PyAstParameter [] getContents();

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyTupleParameter(this);
    }
}
