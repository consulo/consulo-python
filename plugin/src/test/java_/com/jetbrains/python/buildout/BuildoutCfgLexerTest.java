package com.jetbrains.python.buildout;

import consulo.language.ast.IElementType;
import com.jetbrains.python.impl.buildout.config.lexer._BuildoutCfgFlexLexer;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.Reader;

/**
 * @author traff
 */
public abstract class BuildoutCfgLexerTest extends TestCase {

  public void testSimple() throws IOException {
    doTest("[buildout]\n" +
           "develop = .\n" +
           "parts =\n" +
           "  xprompt\n" +
             "src/test\n",
           "[[, []",
           "[buildout, SECTION_NAME]",
           "[], ]]",
           "[\n, WHITESPACE]",
           "[develop , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[ ., VALUE_CHARACTERS]",
           "[\n, WHITESPACE]",
           "[parts , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[\n, WHITESPACE]",
           "[  xprompt, VALUE_CHARACTERS]",
           "[\n, WHITESPACE]",
           "[  test, VALUE_CHARACTERS]",
           "[\n, WHITESPACE]");
  }

  public void testComments() throws IOException {
    doTest("# comment\n" +
           "; comment\n" +
           "[buildout]\n" +
           "develop = value ; comment\n",
           "[# comment\n, COMMENT]",
           "[; comment\n, COMMENT]",
           "[[, []",
           "[buildout, SECTION_NAME]",
           "[], ]]",
           "[\n, WHITESPACE]",
           "[develop , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[ value, VALUE_CHARACTERS]",
           "[ ; comment\n, COMMENT]"
           );
  }

  public void testSpaces() throws IOException {
    doTest( "[buildout]\n" +
           "parts = python django console_scripts\n",
           "[[, []",
           "[buildout, SECTION_NAME]",
           "[], ]]",
           "[\n, WHITESPACE]",
           "[parts , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[ python django console_scripts, VALUE_CHARACTERS]",
           "[\n, WHITESPACE]"
           );
  }


  private static void doTest(String text, String... expected) throws IOException {
    _BuildoutCfgFlexLexer lexer = createLexer(text);
    for (String expectedTokenText : expected) {
      IElementType type = lexer.advance();
      if (type == null) {
        fail("Not enough tokens");
      }
      String tokenText = "[" + lexer.yytext() + ", " + type + "]";
      assertEquals(expectedTokenText, tokenText);
    }
    IElementType type = lexer.advance();
    if (type != null) {
      do {
        String tokenText = "[" + lexer.yytext() + ", " + type + "]";
        System.out.println(tokenText);
        type = lexer.advance();
      }
      while (type != null);
      fail("Too many tokens");
    }
  }

  public static _BuildoutCfgFlexLexer createLexer(String text) {
    _BuildoutCfgFlexLexer lexer = new _BuildoutCfgFlexLexer((Reader)null);
    lexer.reset(text, 0, text.length(), _BuildoutCfgFlexLexer.YYINITIAL);
    return lexer;
  }
}
