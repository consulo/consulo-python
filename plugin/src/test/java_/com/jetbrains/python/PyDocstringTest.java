package com.jetbrains.python;

import java.util.List;

import consulo.language.inject.InjectedLanguageManager;
import consulo.language.lexer.Lexer;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.documentation.doctest.PyDocstringFileType;
import com.jetbrains.python.impl.documentation.doctest.PyDocstringParserDefinition;
import com.jetbrains.python.fixtures.PyTestCase;
import consulo.language.version.LanguageVersionUtil;

/**
 * User: ktisha
 */
public abstract class PyDocstringTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/doctests/";
  }

  public void testWelcome() {
    doTestLexer("  >>> foo()", "Py:SPACE", "Py:IDENTIFIER", "Py:LPAR", "Py:RPAR", "Py:STATEMENT_BREAK");
  }

  public void testDots() {
    doTestLexer(" >>> grouped == { 2:2,\n" +
                "  ...              3:3}", "Py:SPACE", "Py:IDENTIFIER", "Py:SPACE", "Py:EQEQ", "Py:SPACE", "Py:LBRACE", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:COLON", "Py:INTEGER_LITERAL", "Py:COMMA", "Py:LINE_BREAK", "Py:DOT", "Py:SPACE", "Py:INTEGER_LITERAL", "Py:COLON", "Py:INTEGER_LITERAL", "Py:RBRACE", "Py:STATEMENT_BREAK");
  }

  public void testComment() {  //PY-8505
    doTestLexer(" >>> if True:\n" +
                " ... #comm\n"+
                " ...   pass", "Py:SPACE", "Py:IF_KEYWORD", "Py:SPACE", "Py:IDENTIFIER", "Py:COLON", "Py:STATEMENT_BREAK", "Py:LINE_BREAK", "Py:END_OF_LINE_COMMENT", "Py:LINE_BREAK", "Py:INDENT", "Py:PASS_KEYWORD", "Py:STATEMENT_BREAK");
  }
  public void testFunctionName() throws Throwable {
    doCompletionTest();
  }

  public void testClassName() throws Throwable {
    doCompletionTest();
  }

  public void doCompletionTest() throws Throwable {
    String inputDataFileName = getInputDataFileName(getTestName(true));
    String expectedResultFileName = getExpectedResultFileName(getTestName(true));
    myFixture.testCompletion(inputDataFileName, expectedResultFileName);
  }

  // util methods
  private static String getInputDataFileName(String testName) {
    return testName + ".docstring";
  }

  private static String getExpectedResultFileName(String testName) {
    return testName + ".expected.docstring";
  }

  public void testNoErrors() {
    doTestIndentation(false);
  }

  public void testHasErrors() {
    doTestIndentation(true);
  }

  private void doTestIndentation(boolean hasErrors) {
    String inputDataFileName = getTestName(true) + ".py";
    myFixture.configureByFile(inputDataFileName);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myFixture.getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement,TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    for (Pair<PsiElement,TextRange> pair : files) {
      assertEquals(hasErrors, PsiTreeUtil.hasErrorElements(pair.getFirst()));
    }
  }


  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PyDocstringParserDefinition().createLexer(LanguageVersionUtil.findDefaultVersion(PyDocstringFileType.INSTANCE.getLanguage()));
    lexer.start(text);
    int idx = 0;
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
      String tokenName = lexer.getTokenType().toString();
      assertEquals("Token mismatch at position " + idx, expectedTokens[idx], tokenName);
      idx++;
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }
}
