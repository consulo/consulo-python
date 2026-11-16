package com.jetbrains.python.lexer;

import consulo.language.lexer.FlexLexer;

public interface FlexLexerEx extends FlexLexer {

  CharSequence yytext();

  int yylength();

  void yypushback(int number);
}
