package com.jetbrains.python.fixtures;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.PlatformLiteFixture;
import com.jetbrains.python.PythonDialectsTokenSetContributor;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonTokenSetContributor;

/**
 * @author yole
 */
public abstract class PyLexerTestCase extends PlatformLiteFixture {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PyTestCase.initPlatformPrefix();
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    PythonDialectsTokenSetProvider.reset();
  }

  public static void doLexerTest(String text, Lexer lexer, String... expectedTokens) {
    lexer.start(text);
    int idx = 0;
    int tokenPos = 0;
    while (lexer.getTokenType() != null) {
      if (idx >= expectedTokens.length) {
        StringBuilder remainingTokens = new StringBuilder("\"" + lexer.getTokenType().toString() + "\"");
        lexer.advance();
        while (lexer.getTokenType() != null) {
          remainingTokens.append(",");
          remainingTokens.append(" \"").append(lexer.getTokenType().toString()).append("\"");
          lexer.advance();
        }
        fail("Too many tokens. Following tokens: " + remainingTokens.toString());
      }
      assertEquals("Token offset mismatch at position " + idx, tokenPos, lexer.getTokenStart());
      String tokenName = lexer.getTokenType().toString();
      assertEquals("Token mismatch at position " + idx, expectedTokens[idx], tokenName);
      idx++;
      tokenPos = lexer.getTokenEnd();
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }
}
