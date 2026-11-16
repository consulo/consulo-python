// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.lexer;

import com.jetbrains.python.PyTokenTypes;
import consulo.language.ast.TokenSet;


public class PythonIndentingLexer extends PythonIndentingProcessor {
  private static final TokenSet TOKENS_TO_MERGE = PyTokenTypes.FSTRING_TEXT_TOKENS;

  public PythonIndentingLexer() {
    this(PythonLexerKind.REGULAR);
  }

  public PythonIndentingLexer(PythonLexerKind kind) {
    super(new _PythonLexer(null, kind), TOKENS_TO_MERGE);
  }

  boolean addFinalBreak = true;
  @Override
  protected void processSpecialTokens() {
    super.processSpecialTokens();
    int tokenStart = getBaseTokenStart();
    if (getBaseTokenType() == null && addFinalBreak) {
      pushToken(PyTokenTypes.STATEMENT_BREAK, tokenStart, tokenStart);
      addFinalBreak = false;
    }
  }
}
