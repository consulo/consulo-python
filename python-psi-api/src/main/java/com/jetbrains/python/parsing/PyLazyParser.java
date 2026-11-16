package com.jetbrains.python.parsing;

import consulo.python.psi.localize.PyParsingLocalize;
import consulo.language.ast.ASTNode;
import consulo.language.parser.PsiBuilder;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import org.jspecify.annotations.Nullable;

/**
 * Parser for lazily-reparsed {@code PyStatementList} elements.
 * <p>
 * Unlike {@link PyParser}, this class does not implement {@link consulo.language.parser.PsiParser}
 * and does not carry mutable language-level state. All context (language level, builder)
 * is passed explicitly through method parameters.
 */
public final class PyLazyParser {

    private static final Logger LOG = Logger.getInstance(PyLazyParser.class);

    private static class LazyStatementParsing extends StatementParsing {

        LazyStatementParsing(ParsingContext context) {
            super(context);
        }

        void lazyParseStatementList() {
            boolean indentFound = myBuilder.getTokenType() == PyTokenTypes.INDENT;
            if (indentFound) {
                myBuilder.advanceLexer();

                if (myBuilder.eof()) {
                    myBuilder.error(PyParsingLocalize.expectedStatement());
                }

                while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
                    parseStatement();
                }

                if (!myBuilder.eof()) {
                    if (myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
                        LOG.error("Expected DEDENT token, got " + myBuilder.getTokenType());
                    }
                    myBuilder.advanceLexer();
                }
            }
            else {
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
            }
        }
    }

    public static void parseStatementList(IElementType root, PsiBuilder builder, LanguageLevel languageLevel) {
        PsiBuilder.Marker rootMarker = builder.mark();
        ParsingContext context = new ParsingContext(builder, languageLevel);
        StatementParsing statementParser = context.getStatementParser();

        builder.setTokenTypeRemapper(statementParser);
        context.pushScope(context.emptyParsingScope());
        LazyStatementParsing lazyStatementParsing = new LazyStatementParsing(context);
        lazyStatementParsing.lazyParseStatementList();

        rootMarker.done(root);
        rootMarker.setCustomEdgeTokenBinders(LeadingCommentsBinder.INSTANCE, FollowingCommentBinder.INSTANCE);
    }

    public @Nullable ASTNode parseLazyElement(IElementType rootElement,
                                              PsiBuilder builder,
                                              LanguageLevel languageLevel) {
        long start = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
        parseStatementList(rootElement, builder, languageLevel);
        try {
            if (!builder.eof()) {
                LOG.debug("Lazy parseable element of type " + rootElement + " ends before EOF");
                return null;
            }
            ASTNode ast = builder.getTreeBuilt();
            return ast.getFirstChildNode();
        }
        finally {
            if (LOG.isDebugEnabled()) {
                long diff = System.currentTimeMillis() - start;
                double kb = builder.getCurrentOffset() / 1000.0;
                LOG.debug("Parsed " + String.format("%.1f", kb) + "K in " + diff + "ms");
            }
        }
    }
}
