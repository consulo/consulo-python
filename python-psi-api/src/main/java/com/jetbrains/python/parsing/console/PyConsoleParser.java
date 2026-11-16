// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing.console;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.parser.PsiBuilder;
import org.jspecify.annotations.Nullable;

public class PyConsoleParser extends PyParser {

    private static final TokenSet IPYTHON_START_SYMBOLS = TokenSet.create(
        PyConsoleTokenTypes.PLING,
        PyConsoleTokenTypes.QUESTION_MARK,
        PyConsoleTokenTypes.SHELL_COMMAND,
        PyConsoleTokenTypes.MAGIC_COMMAND_LINE,
        PyTokenTypes.COMMA,
        PyTokenTypes.DIV,
        PyTokenTypes.PERC,
        PyTokenTypes.SEMICOLON
    );

    private final PythonConsoleData myPythonConsoleData;

    public PyConsoleParser(PythonConsoleData pythonConsoleData, LanguageLevel languageLevel) {
        myPythonConsoleData = pythonConsoleData;
        myLanguageLevel = languageLevel;
    }

    @Override
    protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel) {
        boolean iPythonStartSymbol = myPythonConsoleData.isIPythonEnabled() && isIPythonSpecialSymbol(builder.getTokenType());
        return new PyConsoleParsingContext(builder, languageLevel, myPythonConsoleData, iPythonStartSymbol);
    }

    public static boolean isIPythonSpecialSymbol(@Nullable IElementType tokenType) {
        return IPYTHON_START_SYMBOLS.contains(tokenType);
    }
}
