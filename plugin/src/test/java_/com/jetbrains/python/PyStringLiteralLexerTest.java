package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.impl.lexer.PyStringLiteralLexer;

/**
 * @author yole
 */
public abstract class PyStringLiteralLexerTest extends PyLexerTestCase {
  public void testBackslashN() {  // PY-1313
    PyLexerTestCase.doLexerTest("u\"\\N{LATIN SMALL LETTER B}\"", new PyStringLiteralLexer(PyTokenTypes.SINGLE_QUOTED_UNICODE),
                                "Py:SINGLE_QUOTED_UNICODE", "VALID_STRING_ESCAPE_TOKEN", "Py:SINGLE_QUOTED_UNICODE");
  }
}
