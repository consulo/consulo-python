// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.BracePair;
import consulo.language.Language;
import consulo.language.PairedBraceMatcher;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] ourBraces = {
        new BracePair(PyTokenTypes.LPAR, PyTokenTypes.RPAR, false),
        new BracePair(PyTokenTypes.LBRACKET, PyTokenTypes.RBRACKET, false),
        new BracePair(PyTokenTypes.LBRACE, PyTokenTypes.RBRACE, false),
        new BracePair(PyTokenTypes.FSTRING_FRAGMENT_START, PyTokenTypes.FSTRING_FRAGMENT_END, true)
    };

    @Override
    public BracePair[] getPairs() {
        return ourBraces;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(IElementType lbraceType, @Nullable IElementType contextType) {
        if (lbraceType == PyTokenTypes.FSTRING_FRAGMENT_START) {
            return true;
        }
        return
            PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(contextType) ||
            contextType == PyTokenTypes.END_OF_LINE_COMMENT ||
            contextType == PyTokenTypes.COLON ||
            contextType == PyTokenTypes.COMMA ||
            contextType == PyTokenTypes.RPAR ||
            contextType == PyTokenTypes.RBRACKET ||
            contextType == PyTokenTypes.RBRACE ||
            contextType == PyTokenTypes.LBRACE ||
            contextType == null;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
