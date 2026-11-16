// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyEllipsisLiteralExpressionImpl extends PyElementImpl implements PyEllipsisLiteralExpression, PyInstantTypeProvider {
    public PyEllipsisLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        // TODO consulo: JB returns PyBuiltinCache.getInstance(this).getEllipsisType() ?: PyAnyType.getUnknown(),
        //  PyBuiltinCache#getEllipsisType (types.pyi "EllipsisType") and PyAnyType are not ported yet
        return null;
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyEllipsisLiteralExpression(this);
    }
}
