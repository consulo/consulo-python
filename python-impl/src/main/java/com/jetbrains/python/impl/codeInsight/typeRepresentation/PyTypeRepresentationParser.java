// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.ast.IElementType;
import consulo.language.parser.PsiBuilder;
import consulo.localize.LocalizeValue;
import consulo.python.psi.localize.PyParsingLocalize;

public class PyTypeRepresentationParser extends PyParser {
    @Override
    protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel) {
        return new ParsingContext(builder, languageLevel) {
            private final ExpressionParsing myExpressionParsing = new TypeRepresentationParser(this);
            private final StatementParsing myStatementParsing = new StatementParsing(this) {
                @Override
                public void parseStatement() {
                    getExpressionParser().parseExpression();
                }
            };

            @Override
            public ExpressionParsing getExpressionParser() {
                return myExpressionParsing;
            }

            @Override
            public StatementParsing getStatementParser() {
                return myStatementParsing;
            }
        };
    }

    private static class TypeRepresentationParser extends ExpressionParsing {
        TypeRepresentationParser(ParsingContext context) {
            super(context);
        }

        @Override
        public void parseExpression() {
            if (myBuilder.eof()) {
                return;
            }
            // Skip statement breaks - they're not part of type expressions
            if (atToken(PyTokenTypes.STATEMENT_BREAK)) {
                myBuilder.advanceLexer();
                return;
            }

            int startOffset = myBuilder.getCurrentOffset();

            // Handle @Todo(...) annotations for unsupported type constructs
            if (atToken(PyTokenTypes.AT)) {
                parseTodoAnnotation();
                return;
            }

            // Try to parse function type (callable syntax) first
            if (!parseFunctionType()) {
                // Not a function type, parse as regular expression
                super.parseSingleExpression(false);
                // Ensure we made progress
                if (myBuilder.getCurrentOffset() == startOffset && !myBuilder.eof()) {
                    // We didn't consume anything, advance to avoid infinite loop
                    myBuilder.error(LocalizeValue.of("Unexpected token")); // NON-NLS
                    myBuilder.advanceLexer();
                }
            }
        }

        /**
         * Parses @Todo(...) style annotations used to mark unsupported type constructs.
         * These are opaque markers and their content is not interpreted.
         */
        private void parseTodoAnnotation() {
            assert atToken(PyTokenTypes.AT);
            PsiBuilder.Marker annotationMarker = myBuilder.mark();
            myBuilder.advanceLexer(); // consume @

            // Consume tokens until we hit a structural boundary
            int depth = 0;
            while (!myBuilder.eof()) {
                IElementType token = myBuilder.getTokenType();
                if (token == PyTokenTypes.LPAR) {
                    depth++;
                    myBuilder.advanceLexer();
                }
                else if (token == PyTokenTypes.RPAR) {
                    if (depth == 0) {
                        break; // Structural boundary (parameter list close, etc.)
                    }
                    depth--;
                    myBuilder.advanceLexer();
                }
                else if (token == PyTokenTypes.COMMA && depth == 0) {
                    break; // Structural boundary
                }
                else if (token == PyTokenTypes.RARROW && depth == 0) {
                    break; // Structural boundary (->)
                }
                else if (token == PyTokenTypes.STATEMENT_BREAK) {
                    break; // Structural boundary
                }
                else {
                    myBuilder.advanceLexer();
                }
            }
            annotationMarker.done(PyTypeRepresentationElementTypes.PLACEHOLDER);
        }

        @Override
        public boolean parsePrimaryExpression(boolean isTargetExpression) {
            // Try to parse as a callable type when we see '(' — handles cases like ((int) -> int) | (str) -> str
            // where the inner callable must be recognized within a parenthesized expression context.
            if (!isTargetExpression && atToken(PyTokenTypes.LPAR) && parseFunctionType()) {
                return true;
            }
            return super.parsePrimaryExpression(isTargetExpression);
        }

        boolean parseFunctionType() {
            PsiBuilder.Marker mark = myBuilder.mark();

            // Parse 'def' types: `def foo.bar(a: int) -> str`
            if (atToken(PyTokenTypes.DEF_KEYWORD)) {
                myBuilder.advanceLexer(); // consume 'def'

                // Parse dotted name (e.g., foo.bar.baz)
                if (!atToken(PyTokenTypes.IDENTIFIER)) {
                    mark.rollbackTo();
                    return false;
                }

                PsiBuilder.Marker nameMarker = myBuilder.mark();
                myBuilder.advanceLexer(); // consume first identifier

                // Parse remaining dotted parts
                while (atToken(PyTokenTypes.DOT)) {
                    myBuilder.advanceLexer(); // consume dot
                    if (!atToken(PyTokenTypes.IDENTIFIER)) {
                        mark.rollbackTo();
                        return false;
                    }
                    myBuilder.advanceLexer(); // consume identifier
                }
                nameMarker.done(PyElementTypes.REFERENCE_EXPRESSION);
            }

            parseTypeParameterList();

            if (atToken(PyTokenTypes.LPAR)) {
                parseParameterTypeList();
                // Check if this is a function type (has ->) or just a parenthesized expression/tuple
                if (atToken(PyTokenTypes.RARROW)) {
                    myBuilder.advanceLexer(); // consume ->
                    // Use parseExpression to handle nested function types in return position
                    parseExpression();
                    mark.done(PyTypeRepresentationElementTypes.FUNCTION_SIGNATURE);
                    return true;
                }
            }
            // Not a function type, rollback
            mark.rollbackTo();
            return false;
        }

        void parseTypeParameterList() {
            if (!atToken(PyTokenTypes.LBRACKET)) {
                return;
            }
            PsiBuilder.Marker typeParamList = myBuilder.mark();
            myBuilder.advanceLexer(); // consume [

            do {
                if (!parseTypeParameter()) {
                    myBuilder.error(PyParsingLocalize.parseExpectedTypeParameter());
                }

                if (atToken(PyTokenTypes.COMMA)) {
                    myBuilder.advanceLexer();
                }
                else if (!atToken(PyTokenTypes.RBRACKET)) {
                    break;
                }
            }
            while (!atToken(PyTokenTypes.RBRACKET));

            checkMatches(PyTokenTypes.RBRACKET, PyParsingLocalize.parseExpectedSymbols(",", "]"));
            typeParamList.done(PyElementTypes.TYPE_PARAMETER_LIST);
        }

        boolean parseTypeParameter() {
            if (!atToken(PyTokenTypes.IDENTIFIER) && !atToken(PyTokenTypes.MULT) && !atToken(PyTokenTypes.EXP)) {
                return false;
            }
            PsiBuilder.Marker typeParamMarker = myBuilder.mark();

            // Handle *Ts or **P
            if (atAnyOfTokens(PyTokenTypes.MULT, PyTokenTypes.EXP)) {
                myBuilder.advanceLexer();
            }

            // Parse identifier
            if (!atToken(PyTokenTypes.IDENTIFIER)) {
                typeParamMarker.drop();
                return false;
            }
            myBuilder.advanceLexer();

            // Parse bound: T: int
            if (atToken(PyTokenTypes.COLON)) {
                myBuilder.advanceLexer();
                parseExpression();
            }

            // Parse default: T = int
            if (atToken(PyTokenTypes.EQ)) {
                myBuilder.advanceLexer();
                parseExpression();
            }

            typeParamMarker.done(PyElementTypes.TYPE_PARAMETER);
            return true;
        }

        void parseParameterTypeList() {
            assert atToken(PyTokenTypes.LPAR);
            PsiBuilder.Marker listMark = myBuilder.mark();
            myBuilder.advanceLexer();

            int paramCount = 0;
            while (!(atAnyOfTokens(PyTokenTypes.RPAR, PyTokenTypes.RARROW, PyTokenTypes.STATEMENT_BREAK) || myBuilder.eof())) {
                int currentOffset = myBuilder.getCurrentOffset();

                if (paramCount > 0) {
                    if (!checkMatches(PyTokenTypes.COMMA, PyParsingLocalize.parseExpectedComma())) {
                        // No comma found, break out of parameter parsing
                        break;
                    }
                }
                boolean parsed;
                if (atToken(PyTokenTypes.DIV)) {
                    // Handle positional-only separator /
                    PsiBuilder.Marker slashMarker = myBuilder.mark();
                    myBuilder.advanceLexer();
                    slashMarker.done(PyElementTypes.SLASH_PARAMETER);
                    parsed = true;
                }
                else if (atToken(PyTokenTypes.MULT)) {
                    PsiBuilder.Marker starMarker = myBuilder.mark();
                    myBuilder.advanceLexer();
                    // Check if this is a named *args parameter (*name: type)
                    if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.COLON) {
                        PsiBuilder.Marker namedVarargMarker = myBuilder.mark();
                        myBuilder.advanceLexer(); // consume identifier
                        myBuilder.advanceLexer(); // consume colon
                        // Parse the type expression, handling * prefix
                        if (atToken(PyTokenTypes.MULT)) {
                            PsiBuilder.Marker innerStarMarker = myBuilder.mark();
                            myBuilder.advanceLexer();
                            parseExpression();
                            innerStarMarker.done(PyElementTypes.STAR_EXPRESSION);
                        }
                        else {
                            parseExpression();
                        }
                        namedVarargMarker.done(PyTypeRepresentationElementTypes.NAMED_PARAMETER_TYPE);
                    }
                    else if (atToken(PyTokenTypes.IDENTIFIER)) {
                        // Unnamed *args, just parse the type
                        parseExpression();
                    }
                    parsed = true;
                    starMarker.done(PyElementTypes.STAR_EXPRESSION);
                }
                else if (atToken(PyTokenTypes.EXP)) {
                    PsiBuilder.Marker doubleStarMarker = myBuilder.mark();
                    myBuilder.advanceLexer();
                    // Check if this is a named **kwargs parameter (**name: type)
                    if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.COLON) {
                        PsiBuilder.Marker namedKwargMarker = myBuilder.mark();
                        myBuilder.advanceLexer(); // consume identifier
                        myBuilder.advanceLexer(); // consume colon
                        // Parse the type expression, handling ** prefix
                        if (atToken(PyTokenTypes.EXP)) {
                            PsiBuilder.Marker innerDoubleStarMarker = myBuilder.mark();
                            myBuilder.advanceLexer();
                            parseExpression();
                            innerDoubleStarMarker.done(PyElementTypes.DOUBLE_STAR_EXPRESSION);
                        }
                        else {
                            parseExpression();
                        }
                        namedKwargMarker.done(PyTypeRepresentationElementTypes.NAMED_PARAMETER_TYPE);
                    }
                    else {
                        // Unnamed **kwargs, just parse the type
                        parseExpression();
                    }
                    parsed = true;
                    doubleStarMarker.done(PyElementTypes.DOUBLE_STAR_EXPRESSION);
                }
                else {
                    if (atToken(PyTokenTypes.AT)) {
                        parseTodoAnnotation();
                        parsed = true;
                    }
                    // Check if this is a named parameter (name: type) or (name: type = default)
                    else if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.COLON) {
                        PsiBuilder.Marker namedParamMarker = myBuilder.mark();
                        myBuilder.advanceLexer(); // consume identifier
                        myBuilder.advanceLexer(); // consume colon
                        // Parse the type expression
                        parseExpression();
                        // Check for default value
                        if (atToken(PyTokenTypes.EQ)) {
                            myBuilder.advanceLexer(); // consume =
                            parseExpression(); // parse default value
                        }
                        namedParamMarker.done(PyTypeRepresentationElementTypes.NAMED_PARAMETER_TYPE);
                        parsed = myBuilder.getCurrentOffset() > currentOffset;
                    }
                    else {
                        // Use parseExpression to handle nested function types and positional types
                        parseExpression();
                        parsed = myBuilder.getCurrentOffset() > currentOffset; // Check we made progress
                    }
                }
                if (!parsed) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                    recoverUntilMatches(PyParsingLocalize.parseExpectedExpression(), PyTokenTypes.COMMA, PyTokenTypes.RPAR,
                        PyTokenTypes.RARROW, PyTokenTypes.STATEMENT_BREAK);
                }
                paramCount++;
            }
            checkMatches(PyTokenTypes.RPAR, PyParsingLocalize.parseExpectedRpar());
            listMark.done(PyTypeRepresentationElementTypes.PARAMETER_TYPE_LIST);
        }

        @Override
        public void parseSubscriptionIndexArgumentList() {
            // In type representation mode, subscript content may contain callable types like (int) -> int.
            // Override the standard parsing to try callable types first for each element.
            if (myBuilder.eof() || atToken(PyTokenTypes.RBRACKET)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            else {
                PsiBuilder.Marker tupleMarker = myBuilder.mark();
                parseSubscriptionTypeArgument();
                boolean multipleElements = false;
                while (atToken(PyTokenTypes.COMMA)) {
                    multipleElements = true;
                    myBuilder.advanceLexer();
                    if (atToken(PyTokenTypes.RBRACKET)) {
                        break;
                    }
                    parseSubscriptionTypeArgument();
                }
                if (multipleElements) {
                    tupleMarker.done(PyElementTypes.TUPLE_EXPRESSION);
                }
                else {
                    tupleMarker.drop();
                }
            }
            checkMatches(PyTokenTypes.RBRACKET, PyParsingLocalize.parseExpectedRbracket());
        }

        private void parseSubscriptionTypeArgument() {
            if (!parseFunctionType()) {
                if (!parseNamedTestExpression(false, false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
        }

        void recoverUntilMatches(LocalizeValue errorMessage, IElementType... types) {
            PsiBuilder.Marker errorMarker = myBuilder.mark();
            boolean hasNonWhitespaceTokens = false;
            while (!(atAnyOfTokens(types) || myBuilder.eof())) {
                // Regular whitespace tokens are already skipped by advancedLexer()
                if (!atToken(PyTokenTypes.STATEMENT_BREAK)) {
                    hasNonWhitespaceTokens = true;
                }
                myBuilder.advanceLexer();
            }
            if (hasNonWhitespaceTokens) {
                errorMarker.error(errorMessage);
            }
            else {
                errorMarker.drop();
            }
        }
    }
}
