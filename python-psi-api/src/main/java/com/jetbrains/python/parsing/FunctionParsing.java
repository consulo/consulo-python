// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import consulo.python.psi.localize.PyParsingLocalize;
import consulo.language.parser.PsiBuilder;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;

import static com.jetbrains.python.parsing.StatementParsing.TOK_ASYNC;


public class FunctionParsing extends Parsing {
    private static final IElementType FUNCTION_TYPE = PyElementTypes.FUNCTION_DECLARATION;

    public FunctionParsing(ParsingContext context) {
        super(context);
    }

    public void parseFunctionDeclaration(PsiBuilder.Marker endMarker, boolean async) {
        assertCurrentToken(PyTokenTypes.DEF_KEYWORD);
        parseFunctionInnards(endMarker, async);
    }

    protected IElementType getFunctionType() {
        return FUNCTION_TYPE;
    }

    protected void parseFunctionInnards(PsiBuilder.Marker functionMarker, boolean async) {
        myBuilder.advanceLexer();
        parseIdentifierOrSkip(PyTokenTypes.LPAR, PyTokenTypes.LBRACKET);
        myContext.getStatementParser().parseTypeParameterList();
        parseParameterList();
        parseReturnTypeAnnotation();
        checkMatches(PyTokenTypes.COLON, PyParsingLocalize.parseExpectedColon());
        ParsingContext context = getParsingContext();
        context.pushScope(context.getScope().withFunction(async));
        getStatementParser().parseSuite(functionMarker, getFunctionType());
        context.popScope();
    }

    public void parseReturnTypeAnnotation() {
        parseReturnTypeAnnotation(true);
    }

    protected void parseReturnTypeAnnotation(boolean checkLanguageLevel) {
        if (myBuilder.getTokenType() == PyTokenTypes.RARROW) {
            if (checkLanguageLevel && myContext.getLanguageLevel().isPython2()) {
                myBuilder.error(PyParsingLocalize.parseFunctionReturnTypeAnnotationsPy2());
            }
            PsiBuilder.Marker maybeReturnAnnotation = myBuilder.mark();
            nextToken();
            if (!myContext.getExpressionParser().parseSingleExpression(false)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            maybeReturnAnnotation.done(PyElementTypes.ANNOTATION);
        }
    }

    public void parseDecoratedDeclaration() {
        assertCurrentToken(PyTokenTypes.AT); // ??? need this?
        PsiBuilder.Marker decoratorStartMarker = myBuilder.mark();
        PsiBuilder.Marker decoListMarker = myBuilder.mark();
        boolean decorated = false;
        while (myBuilder.getTokenType() == PyTokenTypes.AT) {
            PsiBuilder.Marker decoratorMarker = myBuilder.mark();
            myBuilder.advanceLexer();

            myContext.getFunctionParser().parseDecoratorExpression();
            if (atToken(PyTokenTypes.STATEMENT_BREAK)) {
                decoratorMarker.done(PyElementTypes.DECORATOR_CALL);
                nextToken();
            }
            else {
                myBuilder.error(PyParsingLocalize.parseExpectedStatementBreak());
                decoratorMarker.done(PyElementTypes.DECORATOR_CALL);
            }
            decorated = true;
        }
        if (decorated) {
            decoListMarker.done(PyElementTypes.DECORATOR_LIST);
        }
        //else decoListMarker.rollbackTo();
        parseDeclarationAfterDecorator(decoratorStartMarker);
    }

    /**
     * Parses decorator expression after {@code @} according to PEP-614
     */
    public void parseDecoratorExpression() {
        if (!getExpressionParser().parseNamedTestExpression(false, false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
    }

    private void parseDeclarationAfterDecorator(PsiBuilder.Marker endMarker) {
        if (myBuilder.getTokenType() == PyTokenTypes.ASYNC_KEYWORD) {
            myBuilder.advanceLexer();
            parseDeclarationAfterDecorator(endMarker, true);
        }
        else if (atToken(PyTokenTypes.IDENTIFIER, TOK_ASYNC)) {
            advanceAsync(true);
            parseDeclarationAfterDecorator(endMarker, true);
        }
        else {
            parseDeclarationAfterDecorator(endMarker, false);
        }
    }

    protected void parseDeclarationAfterDecorator(PsiBuilder.Marker endMarker, boolean async) {
        if (myBuilder.getTokenType() == PyTokenTypes.DEF_KEYWORD) {
            parseFunctionInnards(endMarker, async);
        }
        else if (myBuilder.getTokenType() == PyTokenTypes.CLASS_KEYWORD) {
            getStatementParser().parseClassDeclaration(endMarker);
        }
        else {
            myBuilder.error(PyParsingLocalize.parseExpectedAtOrDef());
            PsiBuilder.Marker parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
            parameterList.done(PyElementTypes.PARAMETER_LIST);
            myBuilder.mark().done(PyElementTypes.STATEMENT_LIST); // To have non-empty empty statement list
            endMarker.done(getFunctionType());
        }
    }

    public void parseParameterList() {
        PsiBuilder.Marker parameterList;
        if (myBuilder.getTokenType() != PyTokenTypes.LPAR) {
            myBuilder.error(PyParsingLocalize.parseExpectedLpar());
            parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
            parameterList.done(PyElementTypes.PARAMETER_LIST);
            return;
        }
        parseParameterListContents(PyTokenTypes.RPAR, true, false);
    }

    public void parseParameterListContents(IElementType endToken, boolean advanceLexer, boolean isLambda) {
        PsiBuilder.Marker parameterList;
        parameterList = myBuilder.mark();
        if (advanceLexer) {
            myBuilder.advanceLexer();
        }

        boolean first = true;
        boolean afterStarParameter = false;
        while (myBuilder.getTokenType() != endToken) {
            if (first) {
                first = false;
            }
            else {
                if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                    myBuilder.advanceLexer();
                }
                else {
                    myBuilder.error(PyParsingLocalize.parseExpectedCommaLparRpar());
                    break;
                }
            }
            if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
                parseParameterSubList();
                continue;
            }
            boolean isStarParameter = atAnyOfTokens(PyTokenTypes.MULT, PyTokenTypes.EXP);
            if (!parseParameter(endToken, isLambda)) {
                if (afterStarParameter && myContext.getLanguageLevel().isOlderThan(LanguageLevel.PYTHON36)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
                break;
            }
            if (isStarParameter) {
                afterStarParameter = true;
            }
        }

        if (myBuilder.getTokenType() == endToken && endToken == PyTokenTypes.RPAR) {
            myBuilder.advanceLexer();
        }

        parameterList.done(PyElementTypes.PARAMETER_LIST);

        if (myBuilder.getTokenType() == endToken && endToken == PyTokenTypes.COLON) {
            myBuilder.advanceLexer();
        }
    }

    protected boolean parseParameter(IElementType endToken, boolean isLambda) {
        PsiBuilder.Marker parameter = myBuilder.mark();

        if (myBuilder.getTokenType() == PyTokenTypes.DIV) {
            myBuilder.advanceLexer();
            parameter.done(PyElementTypes.SLASH_PARAMETER);
            return true;
        }

        boolean isStarParameter = false;
        if (myBuilder.getTokenType() == PyTokenTypes.MULT) {
            myBuilder.advanceLexer();
            if ((myBuilder.getTokenType() == PyTokenTypes.COMMA) || myBuilder.getTokenType() == endToken) {
                if (myContext.getLanguageLevel().isPython2()) {
                    parameter.rollbackTo();
                    parameter = myBuilder.mark();
                    advanceError(myBuilder, PyParsingLocalize.parseSingleStarParameterNotSupportedPy2());
                }
                parameter.done(PyElementTypes.SINGLE_STAR_PARAMETER);
                return true;
            }

            isStarParameter = true;
        }
        else if (myBuilder.getTokenType() == PyTokenTypes.EXP) {
            myBuilder.advanceLexer();
            isStarParameter = true;
        }
        if (matchToken(PyTokenTypes.IDENTIFIER)) {
            if (!isLambda) {
                parseParameterAnnotation();
            }
            if (!isStarParameter && matchToken(PyTokenTypes.EQ)) {
                if (!getExpressionParser().parseSingleExpression(false)) {
                    PsiBuilder.Marker invalidElements = myBuilder.mark();
                    while (!atAnyOfTokens(endToken, PyTokenTypes.LINE_BREAK, PyTokenTypes.COMMA, null)) {
                        nextToken();
                    }
                    invalidElements.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
            parameter.done(PyElementTypes.NAMED_PARAMETER);
        }
        else {
            parameter.rollbackTo();
            if (atToken(endToken)) {
                return false;
            }
            PsiBuilder.Marker invalidElements = myBuilder.mark();
            while (!atToken(endToken) && !atAnyOfTokens(PyTokenTypes.LINE_BREAK, PyTokenTypes.COMMA, null)) {
                nextToken();
            }
            invalidElements.error(PyParsingLocalize.parseExpectedFormalParamName());
            return atToken(endToken) || atToken(PyTokenTypes.COMMA);
        }
        return true;
    }

    public void parseParameterAnnotation() {
        parseParameterAnnotation(true);
    }

    protected void parseParameterAnnotation(boolean checkLanguageLevel) {
        if (atToken(PyTokenTypes.COLON)) {
            if (checkLanguageLevel && myContext.getLanguageLevel().isPython2()) {
                myBuilder.error(PyParsingLocalize.parseFunctionTypeAnnotationsPy2());
            }
            PsiBuilder.Marker annotationMarker = myBuilder.mark();
            nextToken();
            if (!getExpressionParser().parseSingleExpression(false)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            annotationMarker.done(PyElementTypes.ANNOTATION);
        }
    }

    protected void parseParameterSubList() {
        assertCurrentToken(PyTokenTypes.LPAR);
        PsiBuilder.Marker tuple = myBuilder.mark();
        myBuilder.advanceLexer();
        while (true) {
            if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
                PsiBuilder.Marker parameter = myBuilder.mark();
                myBuilder.advanceLexer();
                parameter.done(PyElementTypes.NAMED_PARAMETER);
            }
            else if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
                parseParameterSubList();
            }
            if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
                myBuilder.advanceLexer();
                break;
            }
            if (myBuilder.getTokenType() != PyTokenTypes.COMMA) {
                myBuilder.error(PyParsingLocalize.parseExpectedCommaLparRpar());
                break;
            }
            myBuilder.advanceLexer();
        }
        if (myBuilder.getTokenType() == PyTokenTypes.EQ) {
            myBuilder.advanceLexer();
            getExpressionParser().parseSingleExpression(false);
        }
        tuple.done(PyElementTypes.TUPLE_PARAMETER);
    }
}
