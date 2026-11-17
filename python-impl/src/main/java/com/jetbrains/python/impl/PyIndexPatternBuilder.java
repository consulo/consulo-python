// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.lexer.Lexer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.search.IndexPatternBuilder;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyIndexPatternBuilder implements IndexPatternBuilder {
    public static final TokenSet COMMENTS = TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT, PyTokenTypes.DOCSTRING);

    @Override
    public @Nullable Lexer getIndexingLexer(PsiFile file) {
        if (file instanceof PyFile) {
            return new PythonLexer();
        }
        return null;
    }

    @Override
    public @Nullable TokenSet getCommentTokenSet(PsiFile file) {
        if (file instanceof PyFile) {
            return COMMENTS;
        }
        return null;
    }

    @Override
    public int getCommentStartDelta(IElementType tokenType) {
        return tokenType == PyTokenTypes.END_OF_LINE_COMMENT
            ? 1
            : tokenType == PyTokenTypes.DOCSTRING
            ? 3
            : 0;
    }

    @Override
    public int getCommentEndDelta(IElementType tokenType) {
        return tokenType == PyTokenTypes.DOCSTRING ? 3 : 0;
    }

    @Override
    public String getCharsAllowedInContinuationPrefix(IElementType tokenType) {
        return tokenType == PyTokenTypes.END_OF_LINE_COMMENT ? "#" : "";
    }
}
