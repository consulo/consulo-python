// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.WordSelectionerFilter;
import consulo.language.psi.PsiElement;

@ExtensionImpl
public final class PyBasicWordSelectionFilter implements WordSelectionerFilter {
    @Override
    public boolean canSelect(PsiElement element) {
        IElementType elementType = element.getNode().getElementType();
        if (PyTokenTypes.STRING_NODES.contains(elementType)) {
            return false;
        }
        if (PyTokenTypes.FSTRING_LITERAL_TOKENS.contains(elementType)) {
            return false;
        }
        return true;
    }
}
