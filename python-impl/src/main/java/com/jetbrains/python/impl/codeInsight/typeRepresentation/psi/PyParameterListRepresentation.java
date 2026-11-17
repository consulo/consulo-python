// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation.psi;

import com.jetbrains.python.impl.codeInsight.typeRepresentation.PyTypeRepresentationElementTypes;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySlashParameter;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;

import java.util.ArrayList;
import java.util.List;

public class PyParameterListRepresentation extends PyElementImpl {
    public PyParameterListRepresentation(ASTNode astNode) {
        super(astNode);
    }

    public List<PsiElement> getParameters() {
        List<PsiElement> result = new ArrayList<>();
        for (PsiElement it : getChildren()) {
            if (it instanceof PyExpression ||
                it instanceof PySlashParameter ||
                it instanceof PyNamedParameterTypeRepresentation ||
                PsiUtilCore.getElementType(it) == PyTypeRepresentationElementTypes.PLACEHOLDER) {
                result.add(it);
            }
        }
        return result;
    }
}
