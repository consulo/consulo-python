// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation.psi;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public class PyNamedParameterTypeRepresentation extends PyElementImpl {
    public PyNamedParameterTypeRepresentation(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public @Nullable String getName() {
        ASTNode identifier = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
        return identifier != null ? identifier.getText() : null;
    }

    public @Nullable PyExpression getTypeExpression() {
        return findChildByClass(PyExpression.class);
    }

    public @Nullable PyExpression getDefaultValue() {
        ASTNode eqNode = getNode().findChildByType(PyTokenTypes.EQ);
        if (eqNode == null) {
            return null;
        }
        return PsiTreeUtil.getNextSiblingOfType(eqNode.getPsi(), PyExpression.class);
    }

    @Override
    public String toString() {
        return super.toString() + "('" + getName() + "')";
    }
}
