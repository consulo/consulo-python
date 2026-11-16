// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import consulo.localize.LocalizeValue;
import consulo.python.psi.localize.PyParsingLocalize;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.WhitespacesAndCommentsBinder;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import org.jspecify.annotations.Nullable;



public class ExpressionParsing extends Parsing {
    private static final Logger LOG = Logger.getInstance(ExpressionParsing.class);
    public static final WhitespacesAndCommentsBinder CONSUME_COMMENTS_AND_SPACES_TO_LEFT = (tokens, atStreamEdge, getter) -> tokens.size();

    public ExpressionParsing(ParsingContext context) {
        super(context);
    }

    public boolean parsePrimaryExpression(boolean isTargetExpression) {
        IElementType firstToken = myBuilder.getTokenType();
        if (isIdentifier(myBuilder)) {
            if (isTargetExpression) {
                buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
            }
            else {
                buildTokenElement(getReferenceType(), myBuilder);
            }
            return true;
        }
        else if (firstToken == PyTokenTypes.INTEGER_LITERAL) {
            buildTokenElement(PyElementTypes.INTEGER_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (firstToken == PyTokenTypes.FLOAT_LITERAL) {
            buildTokenElement(PyElementTypes.FLOAT_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (firstToken == PyTokenTypes.IMAGINARY_LITERAL) {
            buildTokenElement(PyElementTypes.IMAGINARY_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (firstToken == PyTokenTypes.NONE_KEYWORD) {
            buildTokenElement(PyElementTypes.NONE_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (firstToken == PyTokenTypes.ELLIPSIS_LITERAL) {
            buildTokenElement(PyElementTypes.ELLIPSIS_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (firstToken == PyTokenTypes.TRUE_KEYWORD ||
                 firstToken == PyTokenTypes.FALSE_KEYWORD ||
                 firstToken == PyTokenTypes.DEBUG_KEYWORD) {
            buildTokenElement(PyElementTypes.BOOL_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (PyTokenTypes.STRING_NODES.contains(firstToken) || firstToken == PyTokenTypes.FSTRING_START) {
            return parseStringLiteralExpression();
        }
        else if (firstToken == PyTokenTypes.LPAR) {
            parseParenthesizedExpression(isTargetExpression);
            return true;
        }
        else if (firstToken == PyTokenTypes.LBRACKET) {
            parseListLiteralExpression(myBuilder, isTargetExpression);
            return true;
        }
        else if (firstToken == PyTokenTypes.LBRACE) {
            parseDictOrSetDisplay();
            return true;
        }
        else if (firstToken == PyTokenTypes.TICK) {
            parseReprExpression(myBuilder);
            return true;
        }
        else if (parseEllipsis()) {
            return true;
        }
        return false;
    }

    public boolean parseStringLiteralExpression() {
        PsiBuilder builder = myContext.getBuilder();
        IElementType tokenType = builder.getTokenType();
        if (PyTokenTypes.STRING_NODES.contains(tokenType) || tokenType == PyTokenTypes.FSTRING_START) {
            PsiBuilder.Marker marker = builder.mark();
            while (true) {
                tokenType = builder.getTokenType();
                if (PyTokenTypes.STRING_NODES.contains(tokenType)) {
                    nextToken();
                }
                else if (tokenType == PyTokenTypes.FSTRING_START) {
                    parseFormattedStringNode();
                }
                else {
                    break;
                }
            }
            marker.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
            return true;
        }
        return false;
    }

    private void parseFormattedStringNode() {
        PsiBuilder builder = myContext.getBuilder();
        if (atToken(PyTokenTypes.FSTRING_START)) {
            String prefixThenQuotes = builder.getTokenText();
            assert prefixThenQuotes != null;
            String openingQuotes = prefixThenQuotes.replaceFirst("^[UuBbCcRrFfTt]*", "");
            PsiBuilder.Marker marker = builder.mark();
            nextToken();
            while (true) {
                if (atAnyOfTokens(PyTokenTypes.FSTRING_TEXT_TOKENS)) {
                    nextToken();
                }
                else if (atToken(PyTokenTypes.FSTRING_FRAGMENT_START)) {
                    parseFStringFragment();
                }
                else if (atToken(PyTokenTypes.FSTRING_END)) {
                    if (builder.getTokenText().equals(openingQuotes)) {
                        nextToken();
                    }
                    // Can be the end of an enclosing f-string, so leave it in the stream
                    else {
                        builder.mark().error(PyParsingLocalize.parseExpectedFstringQuote(openingQuotes));
                    }
                    break;
                }
                else if (atToken(PyTokenTypes.STATEMENT_BREAK)) {
                    builder.mark().error(PyParsingLocalize.parseExpectedFstringQuote(openingQuotes));
                    break;
                }
                else {
                    builder.error(PyParsingLocalize.unexpectedFStringToken());
                    break;
                }
            }
            marker.done(PyElementTypes.FSTRING_NODE);
        }
    }

    private void parseFStringFragment() {
        PsiBuilder builder = myContext.getBuilder();
        if (atToken(PyTokenTypes.FSTRING_FRAGMENT_START)) {
            PsiBuilder.Marker marker = builder.mark();
            nextToken();
            PsiBuilder.Marker recoveryMarker = builder.mark();
            boolean parsedExpression = myContext.getExpressionParser().parseExpressionOptional();
            if (parsedExpression) {
                recoveryMarker.drop();
                recoveryMarker = builder.mark();
            }
            boolean recovery = !parsedExpression;
            while (!builder.eof() && !atAnyOfTokens(PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION,
                                                    PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START,
                                                    PyTokenTypes.FSTRING_FRAGMENT_END,
                                                    PyTokenTypes.FSTRING_END,
                                                    PyTokenTypes.STATEMENT_BREAK,
                                                    PyTokenTypes.EQ)) {
                nextToken();
                recovery = true;
            }
            if (recovery) {
                recoveryMarker.error(parsedExpression ? PyParsingLocalize.unexpectedExpressionPart() : PyParsingLocalize.parseExpectedExpression());
                recoveryMarker.setCustomEdgeTokenBinders(null, CONSUME_COMMENTS_AND_SPACES_TO_LEFT);
            }
            else {
                recoveryMarker.drop();
            }

            matchToken(PyTokenTypes.EQ);
            boolean hasTypeConversion = matchToken(PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION);
            boolean hasFormatPart = atToken(PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START);
            if (hasFormatPart) {
                parseFStringFragmentFormatPart();
            }
            LocalizeValue errorMessage = PyParsingLocalize.parseExpectedFstringRbrace();
            if (!hasFormatPart && !atToken(PyTokenTypes.FSTRING_END)) {
                errorMessage = PyParsingLocalize.parseExpectedFstringColonOrRbrace();
                if (!hasTypeConversion) {
                    errorMessage = PyParsingLocalize.parseExpectedFstringTypeConversionOrColonOrRbrace();
                }
            }

            checkMatches(PyTokenTypes.FSTRING_FRAGMENT_END, errorMessage);
            marker.setCustomEdgeTokenBinders(null, CONSUME_COMMENTS_AND_SPACES_TO_LEFT);
            marker.done(PyElementTypes.FSTRING_FRAGMENT);
        }
    }

    private void parseFStringFragmentFormatPart() {
        if (atToken(PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START)) {
            PsiBuilder.Marker marker = myContext.getBuilder().mark();
            nextToken();
            while (true) {
                if (atAnyOfTokens(PyTokenTypes.FSTRING_TEXT_TOKENS)) {
                    nextToken();
                }
                else if (atToken(PyTokenTypes.FSTRING_FRAGMENT_START)) {
                    parseFStringFragment();
                }
                else {
                    break;
                }
            }
            marker.done(PyElementTypes.FSTRING_FRAGMENT_FORMAT_PART);
        }
    }

    private void parseListLiteralExpression(PsiBuilder builder, boolean isTargetExpression) {
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LBRACKET);
        PsiBuilder.Marker expr = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.RBRACKET) {
            builder.advanceLexer();
            expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
            return;
        }
        if (!parseNamedTestExpression(false, isTargetExpression)) {
            builder.error(PyParsingLocalize.parseExpectedExpression());
        }
        if (atForOrAsyncFor()) {
            parseComprehension(expr, PyTokenTypes.RBRACKET, PyElementTypes.LIST_COMP_EXPRESSION);
        }
        else {
            while (builder.getTokenType() != PyTokenTypes.RBRACKET) {
                if (!matchToken(PyTokenTypes.COMMA)) {
                    builder.error(PyParsingLocalize.rbracketOrCommaExpected());
                }
                if (atToken(PyTokenTypes.RBRACKET)) {
                    break;
                }
                if (!parseNamedTestExpression(false, isTargetExpression)) {
                    builder.error(PyParsingLocalize.parseExpectedExprOrCommaOrBracket());
                    break;
                }
            }
            checkMatches(PyTokenTypes.RBRACKET, PyParsingLocalize.parseExpectedRbracket());
            expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
        }
    }

    private void parseComprehension(PsiBuilder.Marker expr,
                                    @Nullable IElementType endToken,
                                    IElementType exprType) {
        assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
        while (true) {
            myBuilder.advanceLexer();
            parseStarTargets();
            parseComprehensionRange(exprType == PyElementTypes.GENERATOR_EXPRESSION);
            while (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
                myBuilder.advanceLexer();
                if (!parseOldExpression()) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
            if (atForOrAsyncFor()) {
                continue;
            }
            if (endToken == null || matchToken(endToken)) {
                break;
            }
            myBuilder.error(PyParsingLocalize.parseExpectedForOrBracket());
            break;
        }
        expr.done(exprType);
    }

    public boolean parseStarTargets() {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseStarExpression(true)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            expr.drop();
            return false;
        }
        if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
            while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                myBuilder.advanceLexer();
                PsiBuilder.Marker expr2 = myBuilder.mark();
                if (!parseStarExpression(true)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                    expr2.rollbackTo();
                    break;
                }
                expr2.drop();
            }
            expr.done(PyElementTypes.TUPLE_EXPRESSION);
        }
        else {
            expr.drop();
        }
        return true;
    }

    protected void parseComprehensionRange(boolean generatorExpression) {
        checkMatches(PyTokenTypes.IN_KEYWORD, PyParsingLocalize.parseExpectedIn());
        boolean result;
        if (generatorExpression) {
            result = parseORTestExpression(false, false);
        }
        else {
            result = parseTupleExpression(false, false, true, false);
        }
        if (!result) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
    }

    private void parseDictOrSetDisplay() {
        LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LBRACE);
        PsiBuilder.Marker expr = myBuilder.mark();
        myBuilder.advanceLexer();

        if (matchToken(PyTokenTypes.RBRACE)) {
            expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
            return;
        }

        if (atToken(PyTokenTypes.EXP)) {
            if (!parseDoubleStarExpression(false)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
                return;
            }
            if (atForOrAsyncFor()) {
                // PEP 798: dict comprehension with double-star unpacking, e.g. {**d for d in dicts}
                parseComprehension(expr, PyTokenTypes.RBRACE, PyElementTypes.DICT_COMP_EXPRESSION);
            }
            else {
                parseDictLiteralContentTail(expr);
            }
            return;
        }

        parseDictOrSetTail(expr, false);
    }

    private void parseDictOrSetTail(PsiBuilder.Marker expr, boolean isDict) {
        PsiBuilder.Marker firstExprMarker = myBuilder.mark();

        if (isDict && !parseSingleExpression(false) || !isDict && !parseNamedTestExpression(false, false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            firstExprMarker.drop();
            expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
            return;
        }

        if (matchToken(PyTokenTypes.COLON)) {
            if (!isDict) {
                firstExprMarker.rollbackTo();
                parseDictOrSetTail(expr, true);
                return;
            }
            parseDictLiteralTail(expr, firstExprMarker);
        }
        else if (atToken(PyTokenTypes.COMMA) || atToken(PyTokenTypes.RBRACE)) {
            firstExprMarker.drop();
            parseSetLiteralTail(expr);
        }
        else if (atForOrAsyncFor()) {
            firstExprMarker.drop();
            parseComprehension(expr, PyTokenTypes.RBRACE, PyElementTypes.SET_COMP_EXPRESSION);
        }
        else {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            firstExprMarker.drop();
            expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
        }
    }

    private void parseDictLiteralTail(PsiBuilder.Marker startMarker, PsiBuilder.Marker firstKeyValueMarker) {
        if (!parseSingleExpression(false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            firstKeyValueMarker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
            if (atToken(PyTokenTypes.RBRACE)) {
                myBuilder.advanceLexer();
            }
            startMarker.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
            return;
        }
        firstKeyValueMarker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
        if (atForOrAsyncFor()) {
            parseComprehension(startMarker, PyTokenTypes.RBRACE, PyElementTypes.DICT_COMP_EXPRESSION);
        }
        else {
            parseDictLiteralContentTail(startMarker);
        }
    }

    private void parseDictLiteralContentTail(PsiBuilder.Marker startMarker) {
        while (myBuilder.getTokenType() != PyTokenTypes.RBRACE) {
            checkMatches(PyTokenTypes.COMMA, PyParsingLocalize.parseExpectedComma());
            if (atToken(PyTokenTypes.EXP)) {
                if (!parseDoubleStarExpression(false)) {
                    break;
                }
            }
            else {
                if (!parseKeyValueExpression()) {
                    break;
                }
            }
        }
        checkMatches(PyTokenTypes.RBRACE, PyParsingLocalize.parseExpectedRbrace());
        startMarker.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
    }

    private boolean parseKeyValueExpression() {
        PsiBuilder.Marker marker = myBuilder.mark();
        if (!parseSingleExpression(false)) {
            marker.drop();
            return false;
        }
        checkMatches(PyTokenTypes.COLON, PyParsingLocalize.parseExpectedColon());
        if (!parseSingleExpression(false)) {
            myBuilder.error(PyParsingLocalize.valueExpressionExpected());
            marker.drop();
            return false;
        }
        marker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
        return true;
    }

    private void parseSetLiteralTail(PsiBuilder.Marker startMarker) {
        while (myBuilder.getTokenType() != PyTokenTypes.RBRACE) {
            checkMatches(PyTokenTypes.COMMA, PyParsingLocalize.parseExpectedComma());
            if (!parseNamedTestExpression(false, false)) {
                break;
            }
        }
        checkMatches(PyTokenTypes.RBRACE, PyParsingLocalize.parseExpectedRbrace());
        startMarker.done(PyElementTypes.SET_LITERAL_EXPRESSION);
    }

    private void parseParenthesizedExpression(boolean isTargetExpression) {
        LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
        PsiBuilder.Marker expr = myBuilder.mark();
        myBuilder.advanceLexer();
        if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
            myBuilder.advanceLexer();
            expr.done(PyElementTypes.TUPLE_EXPRESSION);
        }
        else {
            parseYieldOrTupleExpression(isTargetExpression);
            if (atForOrAsyncFor()) {
                parseComprehension(expr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION);
            }
            else {
                PsiBuilder.Marker err = myBuilder.mark();
                boolean empty = true;
                while (!myBuilder.eof() &&
                       myBuilder.getTokenType() != PyTokenTypes.RPAR &&
                       myBuilder.getTokenType() != PyTokenTypes.LINE_BREAK &&
                       myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK &&
                       myBuilder.getTokenType() != PyTokenTypes.FSTRING_END) {
                    myBuilder.advanceLexer();
                    empty = false;
                }
                if (!empty) {
                    err.error(PyParsingLocalize.unexpectedExpressionSyntax());
                }
                else {
                    err.drop();
                }
                checkMatches(PyTokenTypes.RPAR, PyParsingLocalize.parseExpectedRpar());
                expr.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
            }
        }
    }

    private void parseReprExpression(PsiBuilder builder) {
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TICK);
        PsiBuilder.Marker expr = builder.mark();
        builder.advanceLexer();
        parseExpression();
        checkMatches(PyTokenTypes.TICK, PyParsingLocalize.parseExpectedTick());
        expr.done(PyElementTypes.REPR_EXPRESSION);
    }

    public boolean parseMemberExpression(boolean isTargetExpression) {
        // in sequence a.b.... .c all members but last are always references, and the last may be target.
        boolean recastFirstIdentifier = false;
        boolean recastQualifier = false;
        do {
            boolean firstIdentifierIsTarget = isTargetExpression && !recastFirstIdentifier;
            PsiBuilder.Marker expr = myBuilder.mark();
            if (!parsePrimaryExpression(firstIdentifierIsTarget)) {
                expr.drop();
                return false;
            }

            while (true) {
                IElementType tokenType = myBuilder.getTokenType();
                if (tokenType == PyTokenTypes.DOT) {
                    if (firstIdentifierIsTarget) {
                        recastFirstIdentifier = true;
                        expr.rollbackTo();
                        break;
                    }
                    myBuilder.advanceLexer();
                    checkMatches(PyTokenTypes.IDENTIFIER, PyParsingLocalize.parseExpectedName());
                    if (isTargetExpression && !recastQualifier && !atAnyOfTokens(PyTokenTypes.DOT, PyTokenTypes.LPAR, PyTokenTypes.LBRACKET)) {
                        expr.done(PyElementTypes.TARGET_EXPRESSION);
                    }
                    else {
                        expr.done(getReferenceType());
                    }
                    expr = expr.precede();
                }
                else if (tokenType == PyTokenTypes.LPAR) {
                    parseArgumentList();
                    expr.done(PyElementTypes.CALL_EXPRESSION);
                    expr = expr.precede();
                }
                else if (tokenType == PyTokenTypes.LBRACKET) {
                    myBuilder.advanceLexer();
                    parseSubscriptionIndexArgumentList();
                    expr.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
                    if (isTargetExpression && !recastQualifier) {
                        recastFirstIdentifier = true; // subscription is always a reference
                        recastQualifier = true; // recast non-first qualifiers too
                        expr.rollbackTo();
                        break;
                    }
                    expr = expr.precede();
                }
                else {
                    expr.drop();
                    break;
                }
                recastFirstIdentifier = false; // it is true only after a break; normal flow always unsets it.
                // recastQualifier is untouched, it remembers whether qualifiers were already recast
              }
        }
        while (recastFirstIdentifier);

        return true;
    }

    public void parseSubscriptionIndexArgumentList() {
        if (!parseTupleExpression(false, false, false, true)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
        checkMatches(PyTokenTypes.RBRACKET, PyParsingLocalize.parseExpectedRbracket());
    }

    private boolean parseEllipsis() {
        if (atToken(PyTokenTypes.DOT)) {
            PsiBuilder.Marker maybeEllipsis = myBuilder.mark();
            myBuilder.advanceLexer();
            if (matchToken(PyTokenTypes.DOT) && matchToken(PyTokenTypes.DOT)) {
                maybeEllipsis.done(PyElementTypes.ELLIPSIS_LITERAL_EXPRESSION);
                return true;
            }
            maybeEllipsis.rollbackTo();
        }
        return false;
    }

    public void parseArgumentList() {
        LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
        PsiBuilder.Marker arglist = myBuilder.mark();
        myBuilder.advanceLexer();
        PsiBuilder.Marker genexpr = myBuilder.mark();
        int argNumber = 0;
        while (myBuilder.getTokenType() != PyTokenTypes.RPAR) {
            argNumber++;
            if (argNumber > 1) {
                if (argNumber == 2 && atForOrAsyncFor() && genexpr != null) {
                    parseComprehension(genexpr, null, PyElementTypes.GENERATOR_EXPRESSION);
                    genexpr = null;
                    continue;
                }
                else if (matchToken(PyTokenTypes.COMMA)) {
                    if (atToken(PyTokenTypes.RPAR)) {
                        break;
                    }
                }
                else {
                    myBuilder.error(PyParsingLocalize.parseExpectedCommaOrRpar());
                    break;
                }
            }
            if (myBuilder.getTokenType() == PyTokenTypes.MULT || myBuilder.getTokenType() == PyTokenTypes.EXP) {
                boolean singleStar = myBuilder.getTokenType() == PyTokenTypes.MULT;
                PsiBuilder.Marker starArgMarker = myBuilder.mark();
                myBuilder.advanceLexer();
                if (!parseSingleExpression(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
                if (singleStar && argNumber == 1 && genexpr != null && atForOrAsyncFor()) {
                    // PEP 798: a generator expression with star unpacking passed as the sole argument, e.g. foo(*x for x in y)
                    starArgMarker.done(PyElementTypes.STAR_EXPRESSION);
                }
                else {
                    starArgMarker.done(PyElementTypes.STAR_ARGUMENT_EXPRESSION);
                }
            }
            else {
                if (isIdentifier(myBuilder)) {
                    PsiBuilder.Marker keywordArgMarker = myBuilder.mark();
                    advanceIdentifierLike(myBuilder);
                    if (myBuilder.getTokenType() == PyTokenTypes.EQ) {
                        myBuilder.advanceLexer();
                        if (!parseSingleExpression(false)) {
                            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                        }
                        keywordArgMarker.done(PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION);
                        continue;
                    }
                    keywordArgMarker.rollbackTo();
                }
                if (!parseNamedTestExpression(false, false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                    break;
                }
            }
        }

        if (genexpr != null) {
            genexpr.drop();
        }
        checkMatches(PyTokenTypes.RPAR, PyParsingLocalize.parseExpectedRpar());
        arglist.done(PyElementTypes.ARGUMENT_LIST);
    }

    public boolean parseExpressionOptional() {
        return parseTupleExpression(false, false, false, false);
    }

    public boolean parseExpressionOptional(boolean isTargetExpression) {
        return parseTupleExpression(false, isTargetExpression, false, false);
    }

    public void parseExpression() {
        if (!parseExpressionOptional()) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
    }

    public void parseExpression(boolean stopOnIn, boolean isTargetExpression) {
        if (!parseTupleExpression(stopOnIn, isTargetExpression, false, false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
    }

    public boolean parseYieldOrTupleExpression(boolean isTargetExpression) {
        if (myBuilder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
            PsiBuilder.Marker yieldExpr = myBuilder.mark();
            myBuilder.advanceLexer();
            if (myBuilder.getTokenType() == PyTokenTypes.FROM_KEYWORD) {
                myBuilder.advanceLexer();
                boolean parsed = parseTupleExpression(false, isTargetExpression, false, false);
                if (!parsed) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
                yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
                return parsed;
            }
            else {
                parseTupleExpression(false, isTargetExpression, false, false);
                yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
                return true;
            }
        }
        else {
            return parseTupleExpression(false, isTargetExpression, false, false);
        }
    }

    protected boolean parseTupleExpression(boolean stopOnIn, boolean isTargetExpression, boolean oldTest, boolean isSubscription) {
        PsiBuilder.Marker expr = myBuilder.mark();
        boolean exprParseResult = isSubscription ? parseSubscriptionIndexArgument() :
                                  oldTest ? parseOldTestExpression() :
                                  parseNamedTestExpression(stopOnIn, isTargetExpression);
        if (!exprParseResult) {
            expr.drop();
            return false;
        }
        if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
            while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                myBuilder.advanceLexer();
                PsiBuilder.Marker expr2 = myBuilder.mark();
                exprParseResult = isSubscription ? parseSubscriptionIndexArgument() :
                                  oldTest ? parseOldTestExpression() :
                                  parseNamedTestExpression(stopOnIn, isTargetExpression);
                if (!exprParseResult) {
                    expr2.rollbackTo();
                    break;
                }
                expr2.drop();
            }
            expr.done(PyElementTypes.TUPLE_EXPRESSION);
        }
        else {
            expr.drop();
        }
        return true;
    }

    private boolean parseSubscriptionIndexArgument() {
        PsiBuilder.Marker sliceItem = myBuilder.mark();
        parseSingleExpression(false);
        if (!matchToken(PyTokenTypes.COLON)) {
            sliceItem.rollbackTo();
            return parseNamedTestExpression(false, false);
        }
        parseSingleExpression(false);
        if (myBuilder.getTokenType() == PyTokenTypes.COLON) {
            myBuilder.advanceLexer();
            parseSingleExpression(false);
        }
        sliceItem.done(PyElementTypes.SLICE_ITEM);
        return true;
    }

    public boolean parseSingleExpression(boolean isTargetExpression) {
        return parseTestExpression(false, isTargetExpression);
    }

    public boolean parseOldExpression() {
        if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
            return parseLambdaExpression(false);
        }
        return parseORTestExpression(false, false);
    }

    public boolean parseNamedTestExpression(boolean stopOnIn, boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();

        if (isIdentifier(myBuilder) && myBuilder.lookAhead(1) == PyTokenTypes.COLONEQ) {
            buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);

            myBuilder.advanceLexer();

            if (!parseTestExpression(stopOnIn, false)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }

            expr.done(PyElementTypes.ASSIGNMENT_EXPRESSION);
            return true;
        }
        else if (parseTestExpression(stopOnIn, isTargetExpression)) {
            if (!atToken(PyTokenTypes.COLONEQ)) {
                expr.drop();
                return true;
            }
            else {
                // we intentionally allow syntactically illegal assignment expressions like `self.attr := 42` or `xs[0] := 42` for user convenience
                // but don't parse qualified references in LHS as target expressions (unlike in assignment statements)

                myBuilder.error(PyParsingLocalize.parseExpectedIdentifier());

                myBuilder.advanceLexer();

                if (!parseTestExpression(stopOnIn, false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }

                expr.done(PyElementTypes.ASSIGNMENT_EXPRESSION);
                return false;
            }
        }
        else {
            expr.drop();
            return false;
        }
    }

    private boolean parseTestExpression(boolean stopOnIn, boolean isTargetExpression) {
        if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
            return parseLambdaExpression(false);
        }
        PsiBuilder.Marker condExpr = myBuilder.mark();
        if (!parseORTestExpression(stopOnIn, isTargetExpression)) {
            condExpr.drop();
            return false;
        }
        if (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
            PsiBuilder.Marker conditionMarker = myBuilder.mark();
            myBuilder.advanceLexer();
            if (!parseORTestExpression(stopOnIn, isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            else {
                if (myBuilder.getTokenType() != PyTokenTypes.ELSE_KEYWORD) {
                    if (atToken(PyTokenTypes.COLON)) {   // it's regular if statement. Bracket wasn't closed or new line was lost
                      conditionMarker.rollbackTo();
                        condExpr.drop();
                        return true;
                    }
                    else {
                        myBuilder.error(PyParsingLocalize.parseExpectedElse());
                    }
                }
                else {
                    myBuilder.advanceLexer();
                    if (!parseTestExpression(stopOnIn, isTargetExpression)) {
                        myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                    }
                }
            }
            conditionMarker.drop();
            condExpr.done(PyElementTypes.CONDITIONAL_EXPRESSION);
        }
        else {
            condExpr.drop();
        }
        return true;
    }

    private boolean parseOldTestExpression() {
        if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
            return parseLambdaExpression(true);
        }
        return parseORTestExpression(false, false);
    }

    private boolean parseLambdaExpression(boolean oldTest) {
        PsiBuilder.Marker expr = myBuilder.mark();
        myBuilder.advanceLexer();
        getFunctionParser().parseParameterListContents(PyTokenTypes.COLON, false, true);
        boolean parseExpressionResult = oldTest ? parseOldTestExpression() : parseSingleExpression(false);
        if (!parseExpressionResult) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
        expr.done(PyElementTypes.LAMBDA_EXPRESSION);
        return true;
    }

    protected boolean parseORTestExpression(boolean stopOnIn, boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseANDTestExpression(stopOnIn, isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (myBuilder.getTokenType() == PyTokenTypes.OR_KEYWORD) {
            myBuilder.advanceLexer();
            if (!parseANDTestExpression(stopOnIn, isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseANDTestExpression(boolean stopOnIn, boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseNOTTestExpression(stopOnIn, isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (myBuilder.getTokenType() == PyTokenTypes.AND_KEYWORD) {
            myBuilder.advanceLexer();
            if (!parseNOTTestExpression(stopOnIn, isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseNOTTestExpression(boolean stopOnIn, boolean isTargetExpression) {
        if (myBuilder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
            PsiBuilder.Marker expr = myBuilder.mark();
            myBuilder.advanceLexer();
            if (!parseNOTTestExpression(stopOnIn, isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.PREFIX_EXPRESSION);
            return true;
        }
        else {
            return parseComparisonExpression(stopOnIn, isTargetExpression);
        }
    }

    private boolean parseComparisonExpression(boolean stopOnIn, boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseStarExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }
        if (stopOnIn && atToken(PyTokenTypes.IN_KEYWORD)) {
            expr.drop();
            return true;
        }
        while (PyTokenTypes.COMPARISON_OPERATIONS.contains(myBuilder.getTokenType())) {
            if (atToken(PyTokenTypes.NOT_KEYWORD)) {
                PsiBuilder.Marker notMarker = myBuilder.mark();
                myBuilder.advanceLexer();
                if (!atToken(PyTokenTypes.IN_KEYWORD)) {
                    notMarker.rollbackTo();
                    break;
                }
                notMarker.drop();
                myBuilder.advanceLexer();
            }
            else if (atToken(PyTokenTypes.IS_KEYWORD)) {
                myBuilder.advanceLexer();
                if (myBuilder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
                    myBuilder.advanceLexer();
                }
            }
            else {
                myBuilder.advanceLexer();
            }

            if (!parseBitwiseORExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseStarExpression(boolean isTargetExpression) {
        if (atToken(PyTokenTypes.MULT)) {
            PsiBuilder.Marker starExpr = myBuilder.mark();
            nextToken();
            if (!parseBitwiseORExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                starExpr.drop();
                return false;
            }
            starExpr.done(PyElementTypes.STAR_EXPRESSION);
            return true;
        }
        return parseBitwiseORExpression(isTargetExpression);
    }

    private boolean parseDoubleStarExpression(boolean isTargetExpression) {
        if (atToken(PyTokenTypes.EXP)) {
            PsiBuilder.Marker starExpr = myBuilder.mark();
            nextToken();
            if (!parseBitwiseORExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                starExpr.drop();
                return false;
            }
            starExpr.done(PyElementTypes.DOUBLE_STAR_EXPRESSION);
            return true;
        }
        return parseBitwiseORExpression(isTargetExpression);
    }

    private boolean parseBitwiseORExpression(boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseBitwiseXORExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (atToken(PyTokenTypes.OR)) {
            myBuilder.advanceLexer();
            if (!parseBitwiseXORExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseBitwiseXORExpression(boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseBitwiseANDExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (atToken(PyTokenTypes.XOR)) {
            myBuilder.advanceLexer();
            if (!parseBitwiseANDExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseBitwiseANDExpression(boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseShiftExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (atToken(PyTokenTypes.AND)) {
            myBuilder.advanceLexer();
            if (!parseShiftExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseShiftExpression(boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseAdditiveExpression(myBuilder, isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (PyTokenTypes.SHIFT_OPERATIONS.contains(myBuilder.getTokenType())) {
            myBuilder.advanceLexer();
            if (!parseAdditiveExpression(myBuilder, isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseAdditiveExpression(PsiBuilder myBuilder, boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseMultiplicativeExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }
        while (PyTokenTypes.ADDITIVE_OPERATIONS.contains(myBuilder.getTokenType())) {
            myBuilder.advanceLexer();
            if (!parseMultiplicativeExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    private boolean parseMultiplicativeExpression(boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseUnaryExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }

        while (PyTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(myBuilder.getTokenType())) {
            myBuilder.advanceLexer();
            if (!parseUnaryExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
            expr = expr.precede();
        }

        expr.drop();
        return true;
    }

    protected boolean parseUnaryExpression(boolean isTargetExpression) {
        IElementType tokenType = myBuilder.getTokenType();
        if (PyTokenTypes.UNARY_OPERATIONS.contains(tokenType)) {
            PsiBuilder.Marker expr = myBuilder.mark();
            myBuilder.advanceLexer();
            if (!parseUnaryExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.PREFIX_EXPRESSION);
            return true;
        }
        else {
            return parsePowerExpression(isTargetExpression);
        }
    }

    private boolean parsePowerExpression(boolean isTargetExpression) {
        PsiBuilder.Marker expr = myBuilder.mark();
        if (!parseAwaitExpression(isTargetExpression)) {
            expr.drop();
            return false;
        }

        if (myBuilder.getTokenType() == PyTokenTypes.EXP) {
            myBuilder.advanceLexer();
            if (!parseUnaryExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            expr.done(PyElementTypes.BINARY_EXPRESSION);
        }
        else {
            expr.drop();
        }

        return true;
    }

    private boolean parseAwaitExpression(boolean isTargetExpression) {
        if (atToken(PyTokenTypes.AWAIT_KEYWORD)) {
            PsiBuilder.Marker expr = myBuilder.mark();
            myBuilder.advanceLexer();

            if (!parseMemberExpression(isTargetExpression)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                expr.done(PyElementTypes.PREFIX_EXPRESSION);
            }
            else {
                expr.done(PyElementTypes.PREFIX_EXPRESSION);
            }

            return true;
        }
        else {
            return parseMemberExpression(isTargetExpression);
        }
    }

    private boolean atForOrAsyncFor() {
        if (atToken(PyTokenTypes.FOR_KEYWORD)) {
            return true;
        }
        else if (matchToken(PyTokenTypes.ASYNC_KEYWORD)) {
            if (atToken(PyTokenTypes.FOR_KEYWORD)) {
                return true;
            }
            else {
                myBuilder.error(PyParsingLocalize.forExpected());
                return false;
            }
        }
        return false;
    }
}
