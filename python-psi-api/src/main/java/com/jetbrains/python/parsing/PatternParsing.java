// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import consulo.python.psi.localize.PyParsingLocalize;
import consulo.language.parser.PsiBuilder;
import consulo.language.ast.IElementType;
import consulo.util.collection.Stack;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;

import java.util.function.BooleanSupplier;

public class PatternParsing extends Parsing {
    private final Stack<IElementType> myPendingClosingBraces = new Stack<>();

    protected PatternParsing(ParsingContext context) {
        super(context);
    }

    public boolean parseCasePattern() {
        try {
            return parseMaybeSequencePatternWithoutParentheses();
        }
        finally {
            myPendingClosingBraces.clear();
        }
    }

    private boolean parseMaybeSequencePatternWithoutParentheses() {
        PsiBuilder.Marker mark = myBuilder.mark();
        CommaSeparation result = parseCommaSeparatedPatterns(this::parsePattern);
        if (result != CommaSeparation.NO_ELEMENTS) {
            if (result == CommaSeparation.EXISTS) {
                mark.done(PyElementTypes.SEQUENCE_PATTERN);
            }
            else {
                mark.drop();
            }
            return true;
        }
        mark.drop();
        return false;
    }

    private boolean parsePattern() {
        return parseMaybeAsPattern();
    }

    private boolean parseMaybeAsPattern() {
        PsiBuilder.Marker mark = myBuilder.mark();
        if (parseMaybeOrPattern()) {
            if (atToken(PyTokenTypes.AS_KEYWORD)) {
                nextToken();
                if (atSimpleNameNotUnderscore()) {
                    buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
                }
                else {
                    myBuilder.error(PyParsingLocalize.parseExpectedName());
                }
                mark.done(PyElementTypes.AS_PATTERN);
                consumeIllegalLeftoverExpressionTokens();
            }
            else {
                mark.drop();
            }
            return true;
        }
        mark.drop();
        return false;
    }

    private boolean parseMaybeOrPattern() {
        PsiBuilder.Marker mark = myBuilder.mark();
        if (parseClosedPattern()) {
            if (atToken(PyTokenTypes.OR)) {
                while (atToken(PyTokenTypes.OR)) {
                    nextToken();
                    if (!parseClosedPattern()) {
                        myBuilder.error(PyParsingLocalize.parseExpectedPattern());
                        break;
                    }
                }
                mark.done(PyElementTypes.OR_PATTERN);
            }
            else {
                mark.drop();
            }
            return true;
        }
        mark.drop();
        return false;
    }

    private boolean parseClosedPattern() {
        boolean parsed = (parseLiteralPattern() ||
                          parseGroupOrParenthesizedSequencePattern() ||
                          parseSequencePatternInBrackets() ||
                          parseMappingPattern() ||
                          parseClassPattern() ||
                          parseValuePattern() ||
                          parseWildcardPattern() ||
                          parseCapturePattern() ||
                          // The following two are allowed only inside mapping and sequence patterns respectively,
                          // but from recovery standpoint, it's easier to match them universally and then report
                          // wrong contexts in a dedicated annotator.
                          parseDoubleStarPattern() ||
                          parseSingleStarPattern());
        if (parsed) {
            consumeIllegalLeftoverExpressionTokens();
        }
        return parsed;
    }

    private boolean parseClassPattern() {
        if (atToken(PyTokenTypes.IDENTIFIER)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            parseReferenceExpression();
            if (!atToken(PyTokenTypes.LPAR)) {
                mark.rollbackTo();
                return false;
            }
            parseClassPatternArgumentList();
            mark.done(PyElementTypes.CLASS_PATTERN);
            return true;
        }
        return false;
    }

    private void parseClassPatternArgumentList() {
        assert atToken(PyTokenTypes.LPAR);
        PsiBuilder.Marker mark = myBuilder.mark();
        nextToken();
        registerConsumedBracket(PyTokenTypes.LPAR);
        parseCommaSeparatedPatterns(this::parseClassPatternArgument);
        if (checkMatches(PyTokenTypes.RPAR, PyParsingLocalize.parseExpectedRpar())) {
            registerConsumedBracket(PyTokenTypes.RPAR);
        }
        mark.done(PyElementTypes.PATTERN_ARGUMENT_LIST);
    }

    private boolean parseClassPatternArgument() {
        if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.EQ) {
            PsiBuilder.Marker mark = myBuilder.mark();
            nextToken();
            nextToken();
            if (!parsePattern()) {
                myBuilder.error(PyParsingLocalize.parseExpectedPattern());
            }
            mark.done(PyElementTypes.KEYWORD_PATTERN);
            return true;
        }
        return parsePattern();
    }

    private boolean parseMappingPattern() {
        if (atToken(PyTokenTypes.LBRACE)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            nextToken();
            registerConsumedBracket(PyTokenTypes.LBRACE);
            parseCommaSeparatedPatterns(this::parseKeyValuePatternOrDoubleStarPattern);
            if (checkMatches(PyTokenTypes.RBRACE, PyParsingLocalize.parseExpectedRbrace())) {
                registerConsumedBracket(PyTokenTypes.RBRACE);
            }
            mark.done(PyElementTypes.MAPPING_PATTERN);
            return true;
        }
        return false;
    }

    private boolean parseKeyValuePatternOrDoubleStarPattern() {
        if (parseDoubleStarPattern()) {
            consumeIllegalLeftoverExpressionTokens();
            return true;
        }
        // The grammar allows only literal or value patterns here, but for recovery purposes,
        // it's easier to match any pattern, as in dict literals, and then report illegal ones
        // in an annotator.
        PsiBuilder.Marker mark = myBuilder.mark();
        if (parsePattern()) {
            if (!checkMatches(PyTokenTypes.COLON, PyParsingLocalize.parseExpectedColon())) {
                mark.drop();
                return true;
            }
            if (!parsePattern()) {
                myBuilder.error(PyParsingLocalize.parseExpectedPattern());
            }
            mark.done(PyElementTypes.KEY_VALUE_PATTERN);
            return true;
        }
        mark.drop();
        return false;
    }

    private boolean parseDoubleStarPattern() {
        if (atToken(PyTokenTypes.EXP)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            nextToken();
            if (!parseCapturePattern()) {
                myBuilder.error(PyParsingLocalize.parseExpectedName());
            }
            mark.done(PyElementTypes.DOUBLE_STAR_PATTERN);
            return true;
        }
        return false;
    }

    private boolean parseSingleStarPattern() {
        if (atToken(PyTokenTypes.MULT)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            nextToken();
            if (!parseWildcardPattern() && !parseCapturePattern()) {
                myBuilder.error(PyParsingLocalize.parseExpectedNameOrWildcard());
            }
            mark.done(PyElementTypes.SINGLE_STAR_PATTERN);
            return true;
        }
        return false;
    }

    private boolean parseGroupOrParenthesizedSequencePattern() {
        if (atToken(PyTokenTypes.LPAR)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            nextToken();
            registerConsumedBracket(PyTokenTypes.LPAR);
            CommaSeparation result = parseCommaSeparatedPatterns(this::parsePattern);
            if (checkMatches(PyTokenTypes.RPAR, PyParsingLocalize.parseExpectedRpar())) {
                registerConsumedBracket(PyTokenTypes.RPAR);
            }
            mark.done(result == CommaSeparation.SINGLE_NO_COMMA ? PyElementTypes.GROUP_PATTERN : PyElementTypes.SEQUENCE_PATTERN);
            return true;
        }
        return false;
    }

    private boolean parseSequencePatternInBrackets() {
        if (atToken(PyTokenTypes.LBRACKET)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            nextToken();
            registerConsumedBracket(PyTokenTypes.LBRACKET);
            parseCommaSeparatedPatterns(this::parsePattern);
            if (checkMatches(PyTokenTypes.RBRACKET, PyParsingLocalize.parseExpectedRbracket())) {
                registerConsumedBracket(PyTokenTypes.RBRACKET);
            }
            mark.done(PyElementTypes.SEQUENCE_PATTERN);
            return true;
        }
        return false;
    }

    private enum CommaSeparation {
        NO_ELEMENTS,
        SINGLE_NO_COMMA,
        EXISTS,
      }

    private CommaSeparation parseCommaSeparatedPatterns(BooleanSupplier patternParser) {
        CommaSeparation result = CommaSeparation.NO_ELEMENTS;
        boolean afterPattern = patternParser.getAsBoolean();
        if (afterPattern || atToken(PyTokenTypes.COMMA)) {
            result = CommaSeparation.SINGLE_NO_COMMA;
            while (atToken(PyTokenTypes.COMMA)) {
                result = CommaSeparation.EXISTS;
                if (!afterPattern) {
                    myBuilder.error(PyParsingLocalize.parseExpectedPattern());
                }
                nextToken();
                afterPattern = patternParser.getAsBoolean();
                if (!afterPattern && !atToken(PyTokenTypes.COMMA)) {
                    break;
                }
            }
        }
        return result;
    }

    private boolean parseWildcardPattern() {
        if (atToken(PyTokenTypes.IDENTIFIER, "_")) {
            IElementType nextToken = myBuilder.lookAhead(1);
            if (nextToken == PyTokenTypes.DOT || nextToken == PyTokenTypes.LPAR) {
                return false;
            }
            buildTokenElement(PyElementTypes.WILDCARD_PATTERN, myBuilder);
            return true;
        }
        return false;
    }

    private boolean parseCapturePattern() {
        if (atSimpleNameNotUnderscore()) {
            PsiBuilder.Marker mark = myBuilder.mark();
            buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
            mark.done(PyElementTypes.CAPTURE_PATTERN);
            return true;
        }
        return false;
    }

    private boolean atSimpleNameNotUnderscore() {
        if (!atToken(PyTokenTypes.IDENTIFIER) || "_".equals(myBuilder.getTokenText())) {
            return false;
        }
        IElementType afterName = myBuilder.lookAhead(1);
        return afterName != PyTokenTypes.DOT && afterName != PyTokenTypes.LPAR;
    }

    private boolean parseValuePattern() {
        if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.DOT) {
            PsiBuilder.Marker mark = myBuilder.mark();
            parseReferenceExpression();
            mark.done(PyElementTypes.VALUE_PATTERN);
            return true;
        }
        return false;
    }

    /** @noinspection UnusedReturnValue*/
    private boolean parseReferenceExpression() {
        if (atToken(PyTokenTypes.IDENTIFIER)) {
            PsiBuilder.Marker refExpr = myBuilder.mark();
            nextToken();
            refExpr.done(PyElementTypes.REFERENCE_EXPRESSION);
            while (matchToken(PyTokenTypes.DOT)) {
                refExpr = refExpr.precede();
                checkMatches(PyTokenTypes.IDENTIFIER, PyParsingLocalize.parseExpectedName());
                refExpr.done(PyElementTypes.REFERENCE_EXPRESSION);
            }
            return true;
        }
        return false;
    }

    private boolean parseLiteralPattern() {
        PsiBuilder.Marker marker = myBuilder.mark();
        if (parseAllowedLiteralExpression()) {
            marker.done(PyElementTypes.LITERAL_PATTERN);
            return true;
        }
        marker.drop();
        return false;
    }

    private boolean parseAllowedLiteralExpression() {
        if (atAnyOfTokens(PyTokenTypes.STRING_NODES)) {
            PsiBuilder.Marker stringLiteral = myBuilder.mark();
            while (atAnyOfTokens(PyTokenTypes.STRING_NODES)) {
                nextToken();
            }
            stringLiteral.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
            return true;
        }
        else if (atAnyOfTokens(PyTokenTypes.TRUE_KEYWORD, PyTokenTypes.FALSE_KEYWORD)) {
            buildTokenElement(PyElementTypes.BOOL_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (atToken(PyTokenTypes.NONE_KEYWORD)) {
            buildTokenElement(PyElementTypes.NONE_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (atToken(PyTokenTypes.ELLIPSIS_LITERAL)) {
            buildTokenElement(PyElementTypes.ELLIPSIS_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (atAnyOfTokens(PyTokenTypes.PLUS, PyTokenTypes.MINUS) || atAnyOfTokens(PyTokenTypes.NUMERIC_LITERALS)) {
            parseComplexNumber();
            return true;
        }
        return false;
    }

    private void parseComplexNumber() {
        PsiBuilder.Marker complexLiteral = myBuilder.mark();
        if (atAnyOfTokens(PyTokenTypes.PLUS, PyTokenTypes.MINUS)) {
            PsiBuilder.Marker prefixExpression = myBuilder.mark();
            nextToken();
            if (!parseNumericLiteral()) {
                myBuilder.error(PyParsingLocalize.parseExpectedNumber());
            }
            prefixExpression.done(PyElementTypes.PREFIX_EXPRESSION);
        }
        else {
            parseNumericLiteral();
        }
        if (atAnyOfTokens(PyTokenTypes.PLUS, PyTokenTypes.MINUS)) {
            nextToken();
            if (!parseNumericLiteral()) {
                myBuilder.error(PyParsingLocalize.parseExpectedNumber());
            }
            complexLiteral.done(PyElementTypes.BINARY_EXPRESSION);
        }
        else {
            complexLiteral.drop();
        }
    }

    private boolean parseNumericLiteral() {
        if (atToken(PyTokenTypes.INTEGER_LITERAL)) {
            buildTokenElement(PyElementTypes.INTEGER_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (atToken(PyTokenTypes.FLOAT_LITERAL)) {
            buildTokenElement(PyElementTypes.FLOAT_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        else if (atToken(PyTokenTypes.IMAGINARY_LITERAL)) {
            buildTokenElement(PyElementTypes.IMAGINARY_LITERAL_EXPRESSION, myBuilder);
            return true;
        }
        return false;
    }

    private void consumeIllegalLeftoverExpressionTokens() {
        PsiBuilder.Marker mark = myBuilder.mark();
        int initialBalance = myPendingClosingBraces.size();
        boolean consumedAny = false;
        while (!myBuilder.eof() && !atAnyOfTokens(PyTokenTypes.IF_KEYWORD,
                                                  PyTokenTypes.COLON,
                                                  PyTokenTypes.AS_KEYWORD,
                                                  PyTokenTypes.OR,
                                                  PyTokenTypes.STATEMENT_BREAK)) {
            IElementType token = myBuilder.getTokenType();
            if (myPendingClosingBraces.size() == initialBalance &&
                (myPendingClosingBraces.contains(token) || token == PyTokenTypes.COMMA)) {
                break;
            }
            nextToken();
            consumedAny = true;
            if (PyTokenTypes.ALL_BRACES.contains(token)) {
                registerConsumedBracket(token);
            }
        }
        if (consumedAny) {
            mark.error(PyParsingLocalize.unexpectedTokens());
        }
        else {
            mark.drop();
        }
    }

    private void registerConsumedBracket(IElementType bracketType) {
        if (PyTokenTypes.OPEN_BRACES.contains(bracketType)) {
            myPendingClosingBraces.push(bracketType == PyTokenTypes.LBRACE ? PyTokenTypes.RBRACE :
                                        bracketType == PyTokenTypes.LBRACKET ? PyTokenTypes.RBRACKET :
                                        bracketType == PyTokenTypes.LPAR ? PyTokenTypes.RPAR : null);
        }
        else if (PyTokenTypes.CLOSE_BRACES.contains(bracketType) && myPendingClosingBraces.contains(bracketType)) {
            while (myPendingClosingBraces.peek() != bracketType) {
                myPendingClosingBraces.pop();
            }
            IElementType expected = myPendingClosingBraces.pop();
            assert expected == bracketType;
        }
    }
}
