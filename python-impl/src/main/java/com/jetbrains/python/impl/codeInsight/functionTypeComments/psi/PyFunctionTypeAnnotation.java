// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.functionTypeComments.psi;

import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotation extends PyElementImpl {
    public PyFunctionTypeAnnotation(ASTNode astNode) {
        super(astNode);
    }

    public PyParameterTypeList getParameterTypeList() {
        return findNotNullChildByClass(PyParameterTypeList.class);
    }

    public @Nullable PyExpression getReturnType() {
        return findChildByClass(PyExpression.class);
    }
}
