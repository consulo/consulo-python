// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.ast.TokenType;
import consulo.language.lexer.Lexer;
import consulo.language.psi.search.UsageSearchContext;
import consulo.language.psi.stub.BaseFilterLexer;
import consulo.language.psi.stub.OccurrenceConsumer;

public final class PyFilterLexer extends BaseFilterLexer {
    public PyFilterLexer(Lexer originalLexer, OccurrenceConsumer occurrenceConsumer) {
        super(originalLexer, occurrenceConsumer);
    }

    private static final TokenSet ourSkipWordsScanSet = TokenSet.orSet(
        TokenSet.create(
            TokenType.WHITE_SPACE,
            PyTokenTypes.LPAR,
            PyTokenTypes.RPAR,
            PyTokenTypes.LBRACE,
            PyTokenTypes.RBRACE,
            PyTokenTypes.LBRACKET,
            PyTokenTypes.RBRACKET,
            PyTokenTypes.SEMICOLON,
            PyTokenTypes.COMMA,
            PyTokenTypes.DOT,
            PyTokenTypes.AT
        ),
        PyTokenTypes.OPERATIONS
    );

    @Override
    public void advance() {
        IElementType tokenType = myDelegate.getTokenType();

        if (tokenType == PyTokenTypes.IDENTIFIER
            || PyTokenTypes.SCALAR_LITERALS.contains(tokenType)) {
            addOccurrenceInToken(UsageSearchContext.IN_CODE);
        }
        else if (PyTokenTypes.STRING_NODES.contains(tokenType)) {
            scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, true);
            if (tokenType == PyTokenTypes.DOCSTRING) {
                advanceTodoItemCountsInToken();
            }
        }
        else if (PyIndexPatternBuilder.COMMENTS.contains(tokenType)) {
            scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
            advanceTodoItemCountsInToken();
        }
        else if (!ourSkipWordsScanSet.contains(tokenType)) {
            scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
        }


        myDelegate.advance();
    }
}
