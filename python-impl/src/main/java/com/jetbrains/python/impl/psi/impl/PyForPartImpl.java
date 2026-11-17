// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiReference;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.impl.psi.impl.references.PyKeywordReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyForPartImpl extends PyElementImpl implements PyForPart {
    public PyForPartImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PsiReference getReference() {
        ASTNode inNode = getNode().findChildByType(PyTokenTypes.IN_KEYWORD);
        if (inNode == null) {
            return null;
        }

        TextRange range = inNode.getPsi().getTextRangeInParent();
        PyResolveContext resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeAnalysis(getProject(), getContainingFile()));
        return new PyKeywordReference(this, resolveContext, range);
    }
}
