// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import consulo.localize.LocalizeValue;
import consulo.python.psi.localize.PyParsingLocalize;
import consulo.language.parser.PsiBuilder;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import org.jspecify.annotations.Nullable;


public class Parsing {
    protected ParsingContext myContext;
    protected PsiBuilder myBuilder;
    private static final Logger LOG = Logger.getInstance(Parsing.class);

    protected Parsing(ParsingContext context) {
        myContext = context;
        myBuilder = context.getBuilder();
    }

    public ParsingContext getParsingContext() {
        return myContext;
    }

    public ExpressionParsing getExpressionParser() {
        return getParsingContext().getExpressionParser();
    }

    public StatementParsing getStatementParser() {
        return getParsingContext().getStatementParser();
    }

    public FunctionParsing getFunctionParser() {
        return getParsingContext().getFunctionParser();
    }

    public PatternParsing getPatternParser() {
        return getParsingContext().getPatternParser();
    }

    protected boolean checkMatches(IElementType token, LocalizeValue message) {
        if (myBuilder.getTokenType() == token) {
            myBuilder.advanceLexer();
            return true;
        }
        myBuilder.error(message);
        return false;
    }

    protected boolean parseIdentifierOrSkip(IElementType... validSuccessiveTokens) {
        if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
            myBuilder.advanceLexer();
            return true;
        }
        else {
            PsiBuilder.Marker nameExpected = myBuilder.mark();
            if (myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK && !atAnyOfTokens(validSuccessiveTokens)) {
                myBuilder.advanceLexer();
            }
            nameExpected.error(PyParsingLocalize.parseExpectedIdentifier());
            return false;
        }
    }

    protected void assertCurrentToken(PyElementType tokenType) {
        LOG.assertTrue(myBuilder.getTokenType() == tokenType);
    }

    protected boolean atToken(@Nullable IElementType tokenType) {
        return myBuilder.getTokenType() == tokenType;
    }

    protected boolean atToken(IElementType tokenType, String tokenText) {
        return myBuilder.getTokenType() == tokenType && tokenText.equals(myBuilder.getTokenText());
    }

    protected boolean atAnyOfTokens(IElementType... tokenTypes) {
        IElementType currentTokenType = myBuilder.getTokenType();
        for (IElementType tokenType : tokenTypes) {
            if (currentTokenType == tokenType) {
                return true;
            }
        }
        return false;
    }

    protected boolean atAnyOfTokens(TokenSet tokenTypes) {
        return tokenTypes.contains(myBuilder.getTokenType());
    }

    protected boolean matchToken(IElementType tokenType) {
        if (myBuilder.getTokenType() == tokenType) {
            myBuilder.advanceLexer();
            return true;
        }
        return false;
    }

    protected void nextToken() {
        myBuilder.advanceLexer();
    }

    protected void advanceAsync(boolean falseAsync) {
        if (falseAsync) {
            advanceError(myBuilder, PyParsingLocalize.parseAsyncKeywordNotExpectedHere());
        }
        else {
            myBuilder.advanceLexer();
        }
    }

    protected static void advanceIdentifierLike(PsiBuilder builder) {
        if (isFalseIdentifier(builder)) {
            String tokenText = builder.getTokenText();
            advanceError(builder, PyParsingLocalize.parseKeywordCannotBeUsedAsIdentifierPy2(tokenText));
        }
        else {
            builder.advanceLexer();
        }
    }

    protected static void advanceError(PsiBuilder builder, LocalizeValue message) {
        PsiBuilder.Marker err = builder.mark();
        builder.advanceLexer();
        err.error(message);
    }

    protected static boolean isIdentifier(PsiBuilder builder) {
        return builder.getTokenType() == PyTokenTypes.IDENTIFIER || isFalseIdentifier(builder);
    }

    private static boolean isFalseIdentifier(PsiBuilder builder) {
        return builder.getTokenType() == PyTokenTypes.EXEC_KEYWORD ||
               builder.getTokenType() == PyTokenTypes.PRINT_KEYWORD;
    }

    protected static void buildTokenElement(IElementType type, PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        advanceIdentifierLike(builder);
        marker.done(type);
    }

    protected IElementType getReferenceType() {
        return PyElementTypes.REFERENCE_EXPRESSION;
    }
}
