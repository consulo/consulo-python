package com.jetbrains.python.parsing;

import consulo.language.parser.PsiBuilder;
import consulo.logging.Logger;
import consulo.language.ast.IElementType;
import com.jetbrains.python.psi.LanguageLevel;

public class PythonParser {
    protected static final Logger LOGGER = Logger.getInstance(PyParser.class.getName());
    protected LanguageLevel myLanguageLevel;

    public PythonParser() {
        myLanguageLevel = LanguageLevel.getDefault();
    }

    public void setLanguageLevel(LanguageLevel languageLevel) {
        myLanguageLevel = languageLevel;
    }

    public void parseRoot(IElementType root, PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();
        ParsingContext context = createParsingContext(builder, myLanguageLevel);
        StatementParsing statementParser = context.getStatementParser();
        builder.setTokenTypeRemapper(statementParser); // must be done before touching the caching lexer with eof() call.
        boolean lastAfterSemicolon = false;
        while (!builder.eof()) {
            context.pushScope(context.emptyParsingScope());
            if (lastAfterSemicolon) {
                statementParser.parseSimpleStatement();
            }
            else {
                statementParser.parseStatement();
            }
            lastAfterSemicolon = context.getScope().isAfterSemicolon();
            context.popScope();
        }
        rootMarker.done(root);
    }

    protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel) {
        return new ParsingContext(builder, languageLevel);
    }
}
