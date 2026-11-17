// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.functionTypeComments.psi;

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.ast.ASTNode;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyParameterTypeList extends PyElementImpl {
    public PyParameterTypeList(ASTNode astNode) {
        super(astNode);
    }

    public List<PyExpression> getParameterTypes() {
        return findChildrenByType(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    }
}
