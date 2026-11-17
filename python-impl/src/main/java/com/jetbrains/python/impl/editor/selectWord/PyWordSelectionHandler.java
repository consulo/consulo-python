// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.action.AbstractWordSelectioner;
import consulo.language.psi.PsiElement;

@ExtensionImpl
public final class PyWordSelectionHandler extends AbstractWordSelectioner {
    @Override
    public boolean canSelect(PsiElement e) {
        ASTNode astNode = e.getNode();
        return astNode != null && astNode.getElementType() == PyTokenTypes.IDENTIFIER;
    }
}
