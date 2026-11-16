// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import consulo.localize.LocalizeValue;
import consulo.python.psi.localize.PyParsingLocalize;
import consulo.language.parser.ITokenTypeRemapper;
import consulo.language.parser.PsiBuilder;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.util.lang.CharArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementType;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;


public class StatementParsing extends Parsing implements ITokenTypeRemapper {
    private static final Logger LOG = Logger.getInstance(StatementParsing.class);
    protected static final String TOK_FUTURE_IMPORT = PyNames.FUTURE_MODULE;
    protected static final String TOK_PRINT_FUNCTION = "print_function";
    protected static final String TOK_WITH = PyNames.WITH;
    protected static final String TOK_AS = PyNames.AS;
    protected static final String TOK_PRINT = PyNames.PRINT;
    protected static final String TOK_NONE = PyNames.NONE;
    protected static final String TOK_ELLIPSIS = PyNames.ELLIPSIS;
    protected static final String TOK_TRUE = PyNames.TRUE;
    protected static final String TOK_DEBUG = PyNames.DEBUG;
    protected static final String TOK_FALSE = PyNames.FALSE;
    protected static final String TOK_NONLOCAL = PyNames.NONLOCAL;
    protected static final String TOK_EXEC = PyNames.EXEC;
    public static final String TOK_ASYNC = PyNames.ASYNC;
    protected static final String TOK_AWAIT = PyNames.AWAIT;
    protected static final String TOK_OBJECT = PyNames.OBJECT;
    protected static final String TOK_TYPE = PyNames.TYPE;
    protected static final String TOK_IMPORT = PyNames.IMPORT;
    protected static final String TOK_IN = PyNames.IN;
    protected static final String TOK_FROM = PyNames.FROM;
    protected static final String TOK_MATCH = PyNames.MATCH;
    protected static final String TOK_CASE = PyNames.CASE;
    protected static final String TOK_LAZY = PyNames.LAZY;

    protected enum Phase {NONE, FROM, FUTURE, IMPORT} // 'from __future__ import' phase

    private Phase myFutureImportPhase = Phase.NONE;

    protected Set<FutureFeature> myFutureFlags = EnumSet.noneOf(FutureFeature.class);

    public static class ImportTypes {
        public final IElementType statement;
        public final IElementType element;
        public IElementType starElement;

        public ImportTypes(IElementType statement, IElementType element, IElementType starElement) {
            this.statement = statement;
            this.element = element;
            this.starElement = starElement;
        }
    }

    public StatementParsing(ParsingContext context) {
        super(context);
    }

    public void parseStatement() {

        while (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
            myBuilder.advanceLexer();
        }

        IElementType firstToken;
        firstToken = myBuilder.getTokenType();

        if (firstToken == null) {

            return;

        }
        if (firstToken == PyTokenTypes.WHILE_KEYWORD) {
            parseWhileStatement();
            return;
        }
        if (firstToken == PyTokenTypes.IF_KEYWORD) {
            parseIfStatement(PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD, PyTokenTypes.ELSE_KEYWORD, PyElementTypes.IF_STATEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.FOR_KEYWORD) {
            parseForStatement(myBuilder.mark());
            return;
        }
        if (firstToken == PyTokenTypes.TRY_KEYWORD) {
            parseTryStatement();
            return;
        }
        if (firstToken == PyTokenTypes.DEF_KEYWORD) {
            getFunctionParser().parseFunctionDeclaration(myBuilder.mark(), false);
            return;
        }
        if (firstToken == PyTokenTypes.AT) {
            getFunctionParser().parseDecoratedDeclaration();
            return;
        }
        if (firstToken == PyTokenTypes.CLASS_KEYWORD) {
            parseClassDeclaration();
            return;
        }
        if (firstToken == PyTokenTypes.WITH_KEYWORD) {
            parseWithStatement(myBuilder.mark());
            return;
        }
        if (firstToken == PyTokenTypes.ASYNC_KEYWORD) {
            parseAsyncStatement(false);
            return;
        }
        if (atToken(PyTokenTypes.IDENTIFIER, TOK_TYPE)) {
            if (parseTypeAliasStatement()) {
                return;
            }
        }

        if (atToken(PyTokenTypes.IDENTIFIER, TOK_ASYNC)) {
            if (parseAsyncStatement(true)) {
                return;
            }
        }
        if (atToken(PyTokenTypes.IDENTIFIER, TOK_MATCH)) {
            if (parseMatchStatement()) {
                return;
            }
        }
        if (atToken(PyTokenTypes.IDENTIFIER, TOK_LAZY)) {
            IElementType nextToken = myBuilder.lookAhead(1);
            if (nextToken == PyTokenTypes.IMPORT_KEYWORD || nextToken == PyTokenTypes.FROM_KEYWORD) {
                parseLazyImport();
                return;
            }
        }

        parseSimpleStatement();
    }

    private boolean parseTypeAliasStatement() {
        assert atToken(PyTokenTypes.IDENTIFIER, TOK_TYPE);
        PsiBuilder.Marker mark = myBuilder.mark();
        myBuilder.remapCurrentToken(PyTokenTypes.TYPE_KEYWORD);
        nextToken();
        if (!atToken(PyTokenTypes.IDENTIFIER)) {
            mark.rollbackTo();
            myBuilder.remapCurrentToken(PyTokenTypes.IDENTIFIER);
            return false;
        }
        parseIdentifierOrSkip(PyTokenTypes.LBRACKET, PyTokenTypes.EQ);
        parseTypeParameterList();
        checkMatches(PyTokenTypes.EQ, PyParsingLocalize.parseEqExpected());
        myContext.getExpressionParser().parseExpression();
        checkEndOfStatement();
        mark.done(PyElementTypes.TYPE_ALIAS_STATEMENT);
        return true;
    }

    private boolean parseMatchStatement() {
        assert atToken(PyTokenTypes.IDENTIFIER, TOK_MATCH);
        PsiBuilder.Marker mark = myBuilder.mark();
        myBuilder.remapCurrentToken(PyTokenTypes.MATCH_KEYWORD);
        myBuilder.advanceLexer();
        boolean followedBySubject = myContext.getExpressionParser().parseExpressionOptional();
        if (!followedBySubject || !matchToken(PyTokenTypes.COLON)) {
            mark.rollbackTo();
            myBuilder.remapCurrentToken(PyTokenTypes.IDENTIFIER);
            return false;
        }
        parseCaseClauses();
        mark.done(PyElementTypes.MATCH_STATEMENT);
        return true;
    }

    private boolean parseCaseClauses() {
        if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
            myBuilder.advanceLexer();

            boolean indentFound = myBuilder.getTokenType() == PyTokenTypes.INDENT;
            if (indentFound) {
                myBuilder.advanceLexer();
                boolean caseClauseFound = false;
                while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
                    caseClauseFound = true;
                    if (!parseCaseClause()) {
                        PsiBuilder.Marker illegalStatement = myBuilder.mark();
                        parseStatement();
                        illegalStatement.error(PyParsingLocalize.parseExpectedCaseClause());
                    }
                }
                if (!caseClauseFound) {
                    errorAfterComments(PyParsingLocalize.indentExpected());
                }
                if (!myBuilder.eof()) {
                    assert myBuilder.getTokenType() == PyTokenTypes.DEDENT;
                    myBuilder.advanceLexer();
                }
            }
            else {
                myBuilder.error(PyParsingLocalize.indentExpected());
                return false;
            }
        }
        return true;
    }

    private boolean parseCaseClause() {
        if (atToken(PyTokenTypes.IDENTIFIER, TOK_CASE)) {
            PsiBuilder.Marker mark = myBuilder.mark();
            myBuilder.remapCurrentToken(PyTokenTypes.CASE_KEYWORD);
            myBuilder.advanceLexer();
            if (!getPatternParser().parseCasePattern()) {
                PsiBuilder.Marker patternError = myBuilder.mark();
                while (!myBuilder.eof() && !atAnyOfTokens(PyTokenTypes.IF_KEYWORD, PyTokenTypes.COLON, PyTokenTypes.STATEMENT_BREAK)) {
                    nextToken();
                }
                patternError.error(PyParsingLocalize.parseExpectedPattern());
            }
            if (matchToken(PyTokenTypes.IF_KEYWORD)) {
                if (!getExpressionParser().parseNamedTestExpression(false, false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
            parseColonAndSuite();
            mark.done(PyElementTypes.CASE_CLAUSE);
            return true;
        }
        return false;
    }

    protected void parseSimpleStatement() {
        parseSimpleStatement(true);
    }

    protected void parseSimpleStatement(boolean checkLanguageLevel) {
        PsiBuilder builder = myContext.getBuilder();
        IElementType firstToken = builder.getTokenType();
        if (firstToken == null) {
            return;
        }
        if (firstToken == PyTokenTypes.PRINT_KEYWORD && hasPrintStatement()) {
            parsePrintStatement(builder);
            return;
        }
        if (firstToken == PyTokenTypes.ASSERT_KEYWORD) {
            parseAssertStatement();
            return;
        }
        if (firstToken == PyTokenTypes.BREAK_KEYWORD) {
            parseKeywordStatement(builder, PyElementTypes.BREAK_STATEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.CONTINUE_KEYWORD) {
            parseKeywordStatement(builder, PyElementTypes.CONTINUE_STATEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.DEL_KEYWORD) {
            parseDelStatement();
            return;
        }
        if (firstToken == PyTokenTypes.EXEC_KEYWORD) {
            parseExecStatement();
            return;
        }
        if (firstToken == PyTokenTypes.GLOBAL_KEYWORD) {
            parseNameDefiningStatement(PyElementTypes.GLOBAL_STATEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.NONLOCAL_KEYWORD) {
            parseNameDefiningStatement(PyElementTypes.NONLOCAL_STATEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.IMPORT_KEYWORD) {
            parseImportStatement(PyElementTypes.IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.FROM_KEYWORD) {
            parseFromImportStatement();
            return;
        }
        if (firstToken == PyTokenTypes.PASS_KEYWORD) {
            parseKeywordStatement(builder, PyElementTypes.PASS_STATEMENT);
            return;
        }
        if (firstToken == PyTokenTypes.RETURN_KEYWORD) {
            parseReturnStatement(builder);
            return;
        }
        if (firstToken == PyTokenTypes.RAISE_KEYWORD) {
            parseRaiseStatement();
            return;
        }
        PsiBuilder.Marker exprStatement = builder.mark();
        if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
            getExpressionParser().parseYieldOrTupleExpression(false);
            checkEndOfStatement();
            exprStatement.done(PyElementTypes.EXPRESSION_STATEMENT);
            return;
        }
        else if (getExpressionParser().parseExpressionOptional()) {
            IElementType statementType = PyElementTypes.EXPRESSION_STATEMENT;
            if (PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
                statementType = PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
                builder.advanceLexer();
                if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
                    builder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
            else if (atToken(PyTokenTypes.EQ) || (atToken(PyTokenTypes.COLON) && (!checkLanguageLevel || myContext.getLanguageLevel().isPy3K()))) {
                exprStatement.rollbackTo();
                exprStatement = builder.mark();
                getExpressionParser().parseExpression(false, true);
                LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ || builder.getTokenType() == PyTokenTypes.COLON, builder.getTokenType());

                if (builder.getTokenType() == PyTokenTypes.COLON) {
                    statementType = PyElementTypes.TYPE_DECLARATION_STATEMENT;
                    getFunctionParser().parseParameterAnnotation();
                }

                if (builder.getTokenType() == PyTokenTypes.EQ) {
                    statementType = PyElementTypes.ASSIGNMENT_STATEMENT;
                    builder.advanceLexer();

                    while (true) {
                        PsiBuilder.Marker maybeExprMarker = builder.mark();
                        boolean isYieldExpr = builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD;
                        if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
                            maybeExprMarker.drop();
                            builder.error(PyParsingLocalize.parseExpectedExpression());
                            break;
                        }
                        if (builder.getTokenType() == PyTokenTypes.EQ) {
                            if (isYieldExpr) {
                                maybeExprMarker.drop();
                                builder.error(PyParsingLocalize.cannotAssignToYieldExpression());
                            }
                            else {
                                maybeExprMarker.rollbackTo();
                                getExpressionParser().parseExpression(false, true);
                                LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ, builder.getTokenType());
                            }
                            builder.advanceLexer();
                        }
                        else {
                            maybeExprMarker.drop();
                            break;
                        }
                    }
                }
            }

            checkEndOfStatement();
            exprStatement.done(statementType);
            return;
        }
        else {
            exprStatement.drop();
        }

        builder.advanceLexer();
        reportParseStatementError(builder, firstToken);
    }

    protected void reportParseStatementError(PsiBuilder builder, IElementType firstToken) {
        if (firstToken == PyTokenTypes.INCONSISTENT_DEDENT) {
            builder.error(PyParsingLocalize.unindentDoesNotMatchAnyOuterIndent());
        }
        else if (firstToken == PyTokenTypes.INDENT) {
            builder.error(PyParsingLocalize.unexpectedIndent());
        }
        else {
            builder.error(PyParsingLocalize.statementExpectedFound0(firstToken.toString()));
        }
    }

    protected boolean hasPrintStatement() {
        return myContext.getLanguageLevel().hasPrintStatement() && !myFutureFlags.contains(FutureFeature.PRINT_FUNCTION);
    }

    protected void checkEndOfStatement() {
        PsiBuilder builder = myContext.getBuilder();
        ParsingScope scope = getParsingContext().getScope();
        if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
            builder.advanceLexer();
            scope.setAfterSemicolon(false);
        }
        else if (builder.getTokenType() == PyTokenTypes.SEMICOLON) {
            if (!scope.isSuite()) {
                builder.advanceLexer();
                scope.setAfterSemicolon(true);
                if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
                    builder.advanceLexer();
                    scope.setAfterSemicolon(false);
                }
            }
        }
        else if (!builder.eof()) {
            builder.error(PyParsingLocalize.endOfStatementExpected());
        }
    }

    private void parsePrintStatement(PsiBuilder builder) {
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.PRINT_KEYWORD);
        PsiBuilder.Marker statement = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.GTGT) {
            PsiBuilder.Marker target = builder.mark();
            builder.advanceLexer();
            getExpressionParser().parseSingleExpression(false);
            target.done(PyElementTypes.PRINT_TARGET);
        }
        else {
            getExpressionParser().parseSingleExpression(false);
        }
        while (builder.getTokenType() == PyTokenTypes.COMMA) {
            builder.advanceLexer();
            if (getEndOfStatementsTokens().contains(builder.getTokenType())) {
                break;
            }
            getExpressionParser().parseSingleExpression(false);
        }
        checkEndOfStatement();
        statement.done(PyElementTypes.PRINT_STATEMENT);
    }

    protected void parseKeywordStatement(PsiBuilder builder, IElementType statementType) {
        PsiBuilder.Marker statement = builder.mark();
        builder.advanceLexer();
        checkEndOfStatement();
        statement.done(statementType);
    }

    private void parseReturnStatement(PsiBuilder builder) {
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RETURN_KEYWORD);
        PsiBuilder.Marker returnStatement = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() != null && !getEndOfStatementsTokens().contains(builder.getTokenType())) {
            getExpressionParser().parseExpression();
        }
        checkEndOfStatement();
        returnStatement.done(PyElementTypes.RETURN_STATEMENT);
    }

    private void parseDelStatement() {
        assertCurrentToken(PyTokenTypes.DEL_KEYWORD);
        PsiBuilder.Marker delStatement = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseSingleExpression(false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
        while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
            myBuilder.advanceLexer();
            if (!getEndOfStatementsTokens().contains(myBuilder.getTokenType())) {
                if (!getExpressionParser().parseSingleExpression(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
        }

        checkEndOfStatement();
        delStatement.done(PyElementTypes.DEL_STATEMENT);
    }

    private void parseRaiseStatement() {
        assertCurrentToken(PyTokenTypes.RAISE_KEYWORD);
        PsiBuilder.Marker raiseStatement = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!getEndOfStatementsTokens().contains(myBuilder.getTokenType())) {
            getExpressionParser().parseSingleExpression(false);
            if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                myBuilder.advanceLexer();
                getExpressionParser().parseSingleExpression(false);
                if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                    myBuilder.advanceLexer();
                    getExpressionParser().parseSingleExpression(false);
                }
            }
            else if (myBuilder.getTokenType() == PyTokenTypes.FROM_KEYWORD) {
                myBuilder.advanceLexer();
                if (!getExpressionParser().parseSingleExpression(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
        }
        checkEndOfStatement();
        raiseStatement.done(PyElementTypes.RAISE_STATEMENT);
    }

    private void parseAssertStatement() {
        assertCurrentToken(PyTokenTypes.ASSERT_KEYWORD);
        PsiBuilder.Marker assertStatement = myBuilder.mark();
        myBuilder.advanceLexer();
        if (getExpressionParser().parseSingleExpression(false)) {
            if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                myBuilder.advanceLexer();
                if (!getExpressionParser().parseSingleExpression(false)) {
                    myContext.getBuilder().error(PyParsingLocalize.parseExpectedExpression());
                }
            }
            checkEndOfStatement();
        }
        else {
            myContext.getBuilder().error(PyParsingLocalize.parseExpectedExpression());
        }
        assertStatement.done(PyElementTypes.ASSERT_STATEMENT);
    }

    protected void parseImportStatement(IElementType statementType, IElementType elementType) {
        PsiBuilder.Marker importStatement = myContext.getBuilder().mark();
        parseImportStatementAfterMark(importStatement, statementType, elementType);
    }

    private void parseImportStatementAfterMark(PsiBuilder.Marker importStatement,
                                               IElementType statementType,
                                               IElementType elementType) {
        PsiBuilder builder = myContext.getBuilder();
        builder.advanceLexer();
        parseImportElements(elementType, true, false, false);
        checkEndOfStatement();
        importStatement.done(statementType);
    }

    private void parseLazyImport() {
        assert atToken(PyTokenTypes.IDENTIFIER, TOK_LAZY);
        PsiBuilder.Marker mark = myBuilder.mark();
        myBuilder.remapCurrentToken(PyTokenTypes.LAZY_KEYWORD);
        myBuilder.advanceLexer();
        IElementType firstToken = myBuilder.getTokenType();
        if (firstToken == PyTokenTypes.IMPORT_KEYWORD) {
            parseImportStatementAfterMark(mark, PyElementTypes.IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT);
        }
        else {
            parseFromImportStatementAfterMark(mark);
        }
    }

    /*
    Really parses two forms:
    from identifier import id, id... -- may be either relative or absolute
    from . import identifier -- only relative
     */
    private void parseFromImportStatement() {
        PsiBuilder.Marker fromImportStatement = myContext.getBuilder().mark();
        parseFromImportStatementAfterMark(fromImportStatement);
    }

    private void parseFromImportStatementAfterMark(PsiBuilder.Marker fromImportStatement) {
        PsiBuilder builder = myContext.getBuilder();
        assertCurrentToken(PyTokenTypes.FROM_KEYWORD);
        myFutureImportPhase = Phase.FROM;
        builder.advanceLexer();
        boolean from_future = false;
        boolean had_dots = parseRelativeImportDots();
        IElementType statementType = PyElementTypes.FROM_IMPORT_STATEMENT;
        if (had_dots && parseOptionalDottedName() || parseDottedName()) {
            ImportTypes types = checkFromImportKeyword();
            statementType = types.statement;
            IElementType elementType = types.element;
            if (myFutureImportPhase == Phase.FUTURE) {
                myFutureImportPhase = Phase.IMPORT;
                from_future = true;
            }
            if (builder.getTokenType() == PyTokenTypes.MULT) {
                PsiBuilder.Marker star_import_mark = builder.mark();
                builder.advanceLexer();
                star_import_mark.done(types.starElement);
            }
            else if (builder.getTokenType() == PyTokenTypes.LPAR) {
                builder.advanceLexer();
                parseImportElements(elementType, false, true, from_future);
                checkMatches(PyTokenTypes.RPAR, PyParsingLocalize.parseExpectedRpar());
            }
            else {
                parseImportElements(elementType, false, false, from_future);
            }
        }
        else if (had_dots) { // from . import ...
          ImportTypes types = checkFromImportKeyword();
            statementType = types.statement;
            parseImportElements(types.element, false, false, from_future);
        }
        checkEndOfStatement();
        fromImportStatement.done(statementType);
        myFutureImportPhase = Phase.NONE;
    }

    protected ImportTypes checkFromImportKeyword() {
        checkMatches(PyTokenTypes.IMPORT_KEYWORD, PyParsingLocalize.importExpected());
        return new ImportTypes(PyElementTypes.FROM_IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT, PyElementTypes.STAR_IMPORT_ELEMENT);
    }

    /**
     * Parses option dots before imported name.
     *
     * @return true iff there were dots.
     */
    private boolean parseRelativeImportDots() {
        PsiBuilder builder = myContext.getBuilder();
        boolean had_dots = false;
        while (builder.getTokenType() == PyTokenTypes.DOT) {
            had_dots = true;
            builder.advanceLexer();
        }
        return had_dots;
    }

    private void parseImportElements(IElementType elementType, boolean is_module_import, boolean in_parens, boolean from_future) {
        PsiBuilder builder = myContext.getBuilder();
        while (true) {
            PsiBuilder.Marker asMarker = builder.mark();
            if (is_module_import) { // import _
              if (!parseDottedNameAsAware(false)) {
                    asMarker.drop();
                    break;
                }
            }
            else { // from X import _
              String token_text = parseIdentifier(getReferenceType());
                if (from_future) {
                    // TODO: mark all known future feature names
                    if (TOK_PRINT_FUNCTION.equals(token_text)) {
                        myFutureFlags.add(FutureFeature.PRINT_FUNCTION);
                    }
                }
            }
            if (builder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
                builder.advanceLexer();
                parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
            }
            asMarker.done(elementType);
            if (builder.getTokenType() == PyTokenTypes.COMMA) {
                builder.advanceLexer();
                if (in_parens && builder.getTokenType() == PyTokenTypes.RPAR) {
                    break;
                }
            }
            else {
                break;
            }
        }
    }

    private @Nullable String parseIdentifier(IElementType elementType) {
        PsiBuilder.Marker idMarker = myBuilder.mark();
        if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
            String id_text = myBuilder.getTokenText();
            myBuilder.advanceLexer();
            idMarker.done(elementType);
            return id_text;
        }
        else {
            myBuilder.error(PyParsingLocalize.parseExpectedIdentifier());
            idMarker.drop();
        }
        return null;
    }

    public boolean parseOptionalDottedName() {
        return parseDottedNameAsAware(true);
    }

    public boolean parseDottedName() {
        return parseDottedNameAsAware(false);
    }

    // true if identifier was parsed or skipped as optional, false on error
    protected boolean parseDottedNameAsAware(boolean optional) {
        if (myBuilder.getTokenType() != PyTokenTypes.IDENTIFIER) {
            if (optional) {
                return true;
            }
            myBuilder.error(PyParsingLocalize.parseExpectedIdentifier());
            return false;
        }
        PsiBuilder.Marker marker = myBuilder.mark();
        myBuilder.advanceLexer();
        marker.done(getReferenceType());
        while (myBuilder.getTokenType() == PyTokenTypes.DOT) {
            marker = marker.precede();
            myBuilder.advanceLexer();
            checkMatches(PyTokenTypes.IDENTIFIER, PyParsingLocalize.parseExpectedIdentifier());
            marker.done(getReferenceType());
        }
        return true;
    }

    private void parseNameDefiningStatement(PyElementType elementType) {
        PsiBuilder.Marker globalStatement = myBuilder.mark();
        myBuilder.advanceLexer();
        parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
        while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
            myBuilder.advanceLexer();
            parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
        }
        checkEndOfStatement();
        globalStatement.done(elementType);
    }

    private void parseExecStatement() {
        assertCurrentToken(PyTokenTypes.EXEC_KEYWORD);
        PsiBuilder.Marker execStatement = myBuilder.mark();
        myBuilder.advanceLexer();
        getExpressionParser().parseExpression(true, false);
        if (myBuilder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
            myBuilder.advanceLexer();
            getExpressionParser().parseSingleExpression(false);
            if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
                myBuilder.advanceLexer();
                getExpressionParser().parseSingleExpression(false);
            }
        }
        checkEndOfStatement();
        execStatement.done(PyElementTypes.EXEC_STATEMENT);
    }

    protected void parseIfStatement(PyElementType ifKeyword, PyElementType elifKeyword, PyElementType elseKeyword,
                                    PyElementType elementType) {
        assertCurrentToken(ifKeyword);
        PsiBuilder.Marker ifStatement = myBuilder.mark();
        PsiBuilder.Marker ifPart = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseNamedTestExpression(false, false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
        parseColonAndSuite();
        ifPart.done(PyElementTypes.IF_PART_IF);
        PsiBuilder.Marker elifPart = myBuilder.mark();
        while (myBuilder.getTokenType() == elifKeyword) {
            myBuilder.advanceLexer();
            if (!getExpressionParser().parseNamedTestExpression(false, false)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
            parseColonAndSuite();
            elifPart.done(PyElementTypes.IF_PART_ELIF);
            elifPart = myBuilder.mark();
        }
        elifPart.drop(); // we always kept an open extra elif
        PsiBuilder.Marker elsePart = myBuilder.mark();
        if (myBuilder.getTokenType() == elseKeyword) {
            myBuilder.advanceLexer();
            parseColonAndSuite();
            elsePart.done(PyElementTypes.ELSE_PART);
        }
        else {
            elsePart.drop();
        }
        ifStatement.done(elementType);
    }

    private boolean expectColon() {
        if (myBuilder.getTokenType() == PyTokenTypes.COLON) {
            myBuilder.advanceLexer();
            return true;
        }
        else if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
            myBuilder.error(PyParsingLocalize.parseExpectedColon());
            return true;
        }
        PsiBuilder.Marker marker = myBuilder.mark();
        while (!atAnyOfTokens(null, PyTokenTypes.DEDENT, PyTokenTypes.STATEMENT_BREAK, PyTokenTypes.COLON)) {
            myBuilder.advanceLexer();
        }
        boolean result = matchToken(PyTokenTypes.COLON);
        if (!result && atToken(PyTokenTypes.STATEMENT_BREAK)) {
            myBuilder.advanceLexer();
        }
        marker.error(PyParsingLocalize.parseExpectedColon());
        return result;
    }

    private void parseForStatement(PsiBuilder.Marker endMarker) {
        assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
        parseForPart();
        PsiBuilder.Marker elsePart = myBuilder.mark();
        if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
            myBuilder.advanceLexer();
            parseColonAndSuite();
            elsePart.done(PyElementTypes.ELSE_PART);
        }
        else {
            elsePart.drop();
        }
        endMarker.done(PyElementTypes.FOR_STATEMENT);
    }

    protected void parseForPart() {
        PsiBuilder.Marker forPart = myBuilder.mark();
        myBuilder.advanceLexer();
        getExpressionParser().parseStarTargets();
        checkMatches(PyTokenTypes.IN_KEYWORD, PyParsingLocalize.parseExpectedIn());
        getExpressionParser().parseExpression();
        parseColonAndSuite();
        forPart.done(PyElementTypes.FOR_PART);
    }

    private void parseWhileStatement() {
        assertCurrentToken(PyTokenTypes.WHILE_KEYWORD);
        PsiBuilder.Marker statement = myBuilder.mark();
        PsiBuilder.Marker whilePart = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseNamedTestExpression(false, false)) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
        parseColonAndSuite();
        whilePart.done(PyElementTypes.WHILE_PART);
        PsiBuilder.Marker elsePart = myBuilder.mark();
        if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
            myBuilder.advanceLexer();
            parseColonAndSuite();
            elsePart.done(PyElementTypes.ELSE_PART);
        }
        else {
            elsePart.drop();
        }
        statement.done(PyElementTypes.WHILE_STATEMENT);
    }

    private void parseTryStatement() {
        assertCurrentToken(PyTokenTypes.TRY_KEYWORD);
        PsiBuilder.Marker statement = myBuilder.mark();
        PsiBuilder.Marker tryPart = myBuilder.mark();
        myBuilder.advanceLexer();
        parseColonAndSuite();
        tryPart.done(PyElementTypes.TRY_PART);
        boolean haveExceptClause = false;
        if (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
            haveExceptClause = true;
            while (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
                parseExceptClause();
            }
            PsiBuilder.Marker elsePart = myBuilder.mark();
            if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
                myBuilder.advanceLexer();
                parseColonAndSuite();
                elsePart.done(PyElementTypes.ELSE_PART);
            }
            else {
                elsePart.drop();
            }
        }
        PsiBuilder.Marker finallyPart = myBuilder.mark();
        if (myBuilder.getTokenType() == PyTokenTypes.FINALLY_KEYWORD) {
            myBuilder.advanceLexer();
            parseColonAndSuite();
            finallyPart.done(PyElementTypes.FINALLY_PART);
        }
        else {
            finallyPart.drop();
            if (!haveExceptClause) {
                myBuilder.error(PyParsingLocalize.exceptOrFinallyExpected());
                // much better to have a statement of incorrectly determined type
                // than "TRY" and "COLON" tokens attached to nothing
            }
        }
        statement.done(PyElementTypes.TRY_EXCEPT_STATEMENT);
    }

    private void parseExceptClause() {
        PsiBuilder.Marker exceptBlock = myBuilder.mark();
        myBuilder.advanceLexer();

        boolean star = matchToken(PyTokenTypes.MULT);
        if (myBuilder.getTokenType() != PyTokenTypes.COLON) {
            if (myContext.getLanguageLevel().isAtLeast(LanguageLevel.PYTHON314)) {
                // Python 3.14, support PEP-758 syntax: except ImportError, OtherError: ...
                if (!getExpressionParser().parseExpressionOptional(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
                if (myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
                    myBuilder.advanceLexer();
                    if (!getExpressionParser().parseSingleExpression(true)) {
                        myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                    }
                }
            }
            else {
                // Python 2, support syntax: except ImportError, targetE: ...
                if (!getExpressionParser().parseSingleExpression(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
                // support Py3K syntax with 'as' to show an error with a quickfix
                if (myBuilder.getTokenType() == PyTokenTypes.COMMA || myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
                    myBuilder.advanceLexer();
                    if (!getExpressionParser().parseSingleExpression(true)) {
                        myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                    }
                }
            }
        }
        else if (star) {
            myBuilder.error(PyParsingLocalize.parseExpectedExpression());
        }
        parseColonAndSuite();
        exceptBlock.done(PyElementTypes.EXCEPT_PART);
    }

    private void parseColonAndSuite() {
        if (expectColon()) {
            parseSuite();
        }
        else {
            PsiBuilder.Marker mark = myBuilder.mark();
            mark.done(PyElementTypes.STATEMENT_LIST);
        }
    }

    private void parseWithStatement(PsiBuilder.Marker endMarker) {
        assertCurrentToken(PyTokenTypes.WITH_KEYWORD);
        myBuilder.advanceLexer();
        if (!parseParenthesizedWithItems()) {
            if (!parseWithItems(false)) {
                myBuilder.error(PyParsingLocalize.parseExpectedExpression());
            }
        }
        parseColonAndSuite();
        endMarker.done(PyElementTypes.WITH_STATEMENT);
    }

    private boolean parseParenthesizedWithItems() {
        if (!atToken(PyTokenTypes.LPAR)) {
            return false;
        }
        PsiBuilder.Marker leftPar = myBuilder.mark();
        nextToken();
        // Reparse empty parentheses as an empty tuple
        if (!parseWithItems(true)) {
            leftPar.rollbackTo();
            return false;
        }
        if (!matchToken(PyTokenTypes.RPAR)) {
            myBuilder.error(PyParsingLocalize.parseExpectedRpar());
        }
        // Reparse something like "(foo()) as bar" or (foo()).bar as a single WithItem
        if (!atAnyOfTokens(PyTokenTypes.COLON, PyTokenTypes.STATEMENT_BREAK)) {
            leftPar.rollbackTo();
            return false;
        }
        leftPar.drop();
        return true;
    }

    private boolean parseWithItems(boolean insideParentheses) {
        if (!parseWithItem()) {
            return false;
        }
        while (matchToken(PyTokenTypes.COMMA)) {
            if (!parseWithItem()) {
                if (!insideParentheses) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
                break;
            }
        }
        return true;
    }

    private boolean parseWithItem() {
        PsiBuilder.Marker withItem = myBuilder.mark();
        if (!getExpressionParser().parseSingleExpression(false)) {
            withItem.drop();
            return false;
        }
        if (matchToken(PyTokenTypes.AS_KEYWORD)) {
            if (!getExpressionParser().parseSingleExpression(true)) {
                myBuilder.error(PyParsingLocalize.parseExpectedIdentifier());
            }
        }
        withItem.done(PyElementTypes.WITH_ITEM);
        return true;
    }

    private void parseClassDeclaration() {
        PsiBuilder.Marker classMarker = myBuilder.mark();
        parseClassDeclaration(classMarker);
    }

    public void parseClassDeclaration(PsiBuilder.Marker classMarker) {
        assertCurrentToken(PyTokenTypes.CLASS_KEYWORD);
        myBuilder.advanceLexer();
        parseIdentifierOrSkip(PyTokenTypes.LPAR, PyTokenTypes.LBRACKET, PyTokenTypes.COLON);
        parseTypeParameterList();
        if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
            getExpressionParser().parseArgumentList();
        }
        else {
            PsiBuilder.Marker inheritMarker = myBuilder.mark();
            inheritMarker.done(PyElementTypes.ARGUMENT_LIST);
        }
        ParsingContext context = getParsingContext();
        context.pushScope(context.getScope().withClass());
        parseColonAndSuite();
        context.popScope();
        classMarker.done(PyElementTypes.CLASS_DECLARATION);
    }

    private boolean parseAsyncStatement(boolean falseAsync) {
        if (!falseAsync) {
            assertCurrentToken(PyTokenTypes.ASYNC_KEYWORD);
        }
        PsiBuilder.Marker marker = myBuilder.mark();

        advanceAsync(falseAsync);

        IElementType token = myBuilder.getTokenType();
        if (token == PyTokenTypes.DEF_KEYWORD) {
            getFunctionParser().parseFunctionDeclaration(marker, true);
            return true;
        }
        else if (token == PyTokenTypes.WITH_KEYWORD) {
            parseWithStatement(marker);
            return true;
        }
        else if (token == PyTokenTypes.FOR_KEYWORD) {
            parseForStatement(marker);
            return true;
        }
        else {
            if (falseAsync) {
                marker.rollbackTo();
            }
            else {
                marker.drop();
                myBuilder.error(PyParsingLocalize.defOrWithOrForExpected());
            }
            return false;
        }
    }

    public void parseSuite() {
        parseSuite(null, null);
    }

    /**
     * Reports an error after the comments before the current token.
     * A suite that holds only comments keeps them before its error, so the comments stay inside the suite.
     */
    private void errorAfterComments(LocalizeValue message) {
        PsiBuilder.Marker error = myBuilder.mark();
        error.error(message);
        // The builder binds an empty marker before the comments. Bind it after them.
        error.setCustomEdgeTokenBinders((tokens, ignoredAtStreamEdge, ignoredGetter) -> tokens.size(), null);
    }

    public void parseSuite(PsiBuilder.@Nullable Marker endMarker, @Nullable IElementType elType) {
        if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
            myBuilder.advanceLexer();

            PsiBuilder.Marker marker = myBuilder.mark();
            boolean indentFound = myBuilder.getTokenType() == PyTokenTypes.INDENT;
            if (indentFound) {
                myBuilder.advanceLexer();
                boolean statementFound = false;
                while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
                    statementFound = true;
                    parseStatement();
                }
                if (!statementFound) {
                    errorAfterComments(PyParsingLocalize.indentExpected());
                }
            }
            else {
                myBuilder.error(PyParsingLocalize.indentExpected());
            }

            marker.done(PyElementTypes.STATEMENT_LIST);
            marker.setCustomEdgeTokenBinders(LeadingCommentsBinder.INSTANCE, FollowingCommentBinder.INSTANCE);
            if (endMarker != null) {
                endMarker.done(elType);
            }
            if (indentFound && !myBuilder.eof()) {
                assert myBuilder.getTokenType() == PyTokenTypes.DEDENT;
                myBuilder.advanceLexer();
            }
        }
        else {
            PsiBuilder.Marker marker = myBuilder.mark();
            if (myBuilder.eof()) {
                myBuilder.error(PyParsingLocalize.expectedStatement());
            }
            else {
                ParsingContext context = getParsingContext();
                context.pushScope(context.getScope().withSuite());
                parseSimpleStatement();
                context.popScope();
                while (matchToken(PyTokenTypes.SEMICOLON)) {
                    if (matchToken(PyTokenTypes.STATEMENT_BREAK)) {
                        break;
                    }
                    context.pushScope(context.getScope().withSuite());
                    parseSimpleStatement();
                    context.popScope();
                }
            }
            marker.done(PyElementTypes.STATEMENT_LIST);
            if (endMarker != null) {
                endMarker.done(elType);
            }
        }
    }

    public void parseTypeParameterList() {
        if (atToken(PyTokenTypes.LBRACKET)) {
            PsiBuilder.Marker typeParamList = myBuilder.mark();
            nextToken();
            do {
                if (!parseTypeParameter()) {
                    myBuilder.error(PyParsingLocalize.parseExpectedTypeParameter());
                }

                if (atToken(PyTokenTypes.COMMA)) {
                    nextToken();
                }
                else if (!atToken(PyTokenTypes.RBRACKET)) {
                    break;
                }
            }
            while (!atToken(PyTokenTypes.RBRACKET));
            checkMatches(PyTokenTypes.RBRACKET, PyParsingLocalize.parseExpectedSymbols(",", "]"));
            typeParamList.done(PyElementTypes.TYPE_PARAMETER_LIST);
        }
    }

    private boolean parseTypeParameter() {
        if (isIdentifier(myBuilder) || atToken(PyTokenTypes.MULT) || atToken(PyTokenTypes.EXP)) {
            PsiBuilder.Marker typeParamMarker = myBuilder.mark();

            if (atAnyOfTokens(PyTokenTypes.MULT, PyTokenTypes.EXP)) {
                nextToken();
            }

            if (!parseIdentifierOrSkip(PyTokenTypes.RBRACKET, PyTokenTypes.COMMA, PyTokenTypes.COLON, PyTokenTypes.EQ)) {
                typeParamMarker.drop();
                return false;
            }

            if (matchToken(PyTokenTypes.COLON)) {
                if (!myContext.getExpressionParser().parseSingleExpression(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }

            if (matchToken(PyTokenTypes.EQ)) {
                if (!myContext.getExpressionParser().parseSingleExpression(false)) {
                    myBuilder.error(PyParsingLocalize.parseExpectedExpression());
                }
            }
            typeParamMarker.done(PyElementTypes.TYPE_PARAMETER);
            return true;
        }
        return false;
    }

    @Override
    public IElementType filter(IElementType source, int start, int end, CharSequence text) {
        return filter(source, start, end, text, true);
    }

    protected IElementType filter(IElementType source,
                                  int start,
                                  int end,
                                  CharSequence text,
                                  boolean checkLanguageLevel) {
        if (source == PyTokenTypes.IDENTIFIER && isWordAtPosition(text, start, end, TOK_AS)) {
            return PyTokenTypes.AS_KEYWORD;
        }
        else if ( // filter
          (myFutureImportPhase == Phase.FROM) &&
          source == PyTokenTypes.IDENTIFIER &&
          isWordAtPosition(text, start, end, TOK_FUTURE_IMPORT)
          ) {
            myFutureImportPhase = Phase.FUTURE;
            return source;
        }
        else if (source == PyTokenTypes.IDENTIFIER && isWordAtPosition(text, start, end, TOK_WITH)) {
            return PyTokenTypes.WITH_KEYWORD;
        }
        else if (hasPrintStatement() &&
                 source == PyTokenTypes.IDENTIFIER &&
                 isWordAtPosition(text, start, end, TOK_PRINT)) {
            return PyTokenTypes.PRINT_KEYWORD;
        }
        else if ((myContext.getLanguageLevel().isPy3K() || !checkLanguageLevel) &&
                 source == PyTokenTypes.IDENTIFIER) {
            if (isWordAtPosition(text, start, end, TOK_NONE)) {
                return PyTokenTypes.NONE_KEYWORD;
            }
            if (isWordAtPosition(text, start, end, TOK_ELLIPSIS)) {
                return PyTokenTypes.ELLIPSIS_LITERAL;
            }
            if (isWordAtPosition(text, start, end, TOK_TRUE)) {
                return PyTokenTypes.TRUE_KEYWORD;
            }
            if (isWordAtPosition(text, start, end, TOK_FALSE)) {
                return PyTokenTypes.FALSE_KEYWORD;
            }
            if (isWordAtPosition(text, start, end, TOK_DEBUG)) {
                return PyTokenTypes.DEBUG_KEYWORD;
            }
            if (isWordAtPosition(text, start, end, TOK_NONLOCAL)) {
                return PyTokenTypes.NONLOCAL_KEYWORD;
            }
            LanguageLevel languageLevel = myContext.getLanguageLevel();
            if (languageLevel.isAtLeast(LanguageLevel.PYTHON35) || !checkLanguageLevel) {
                if (isWordAtPosition(text, start, end, TOK_ASYNC)) {
                    if (languageLevel.isAtLeast(LanguageLevel.PYTHON37) ||
                        myContext.getScope().isAsync() ||
                        myBuilder.lookAhead(1) == PyTokenTypes.DEF_KEYWORD) {
                        return PyTokenTypes.ASYNC_KEYWORD;
                    }
                }
                if (isWordAtPosition(text, start, end, TOK_AWAIT)) {
                    if (languageLevel.isAtLeast(LanguageLevel.PYTHON37) || myContext.getScope().isAsync()) {
                        return PyTokenTypes.AWAIT_KEYWORD;
                    }
                }
            }
        }
        else if ((myContext.getLanguageLevel().isPython2() || !checkLanguageLevel) &&
                 source == PyTokenTypes.IDENTIFIER) {
            if (isWordAtPosition(text, start, end, TOK_EXEC)) {
                return PyTokenTypes.EXEC_KEYWORD;
            }
        }
        return source;
    }

    protected TokenSet getEndOfStatementsTokens() {
        return PyTokenTypes.END_OF_STATEMENT;
    }

    private static boolean isWordAtPosition(CharSequence text, int start, int end, String tokenText) {
        return CharArrayUtil.regionMatches(text, start, end, tokenText) && end - start == tokenText.length();
    }

}
