package com.jetbrains.python;

import java.util.List;

import jakarta.annotation.Nonnull;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.lexer.Lexer;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.impl.codeInsight.regexp.PythonRegexpLanguage;
import com.jetbrains.python.impl.codeInsight.regexp.PythonRegexpParserDefinition;
import com.jetbrains.python.impl.codeInsight.regexp.PythonVerboseRegexpLanguage;
import com.jetbrains.python.impl.codeInsight.regexp.PythonVerboseRegexpParserDefinition;
import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import consulo.language.version.LanguageVersionUtil;

/**
 * @author yole
 */
public abstract class PyRegexpTest extends PyTestCase {
  public void testNestedCharacterClasses() {  // PY-2908
    doTestHighlighting();
  }

  public void testNestedCharacterClassesLexer() {
    doTestLexer("[][]", "CLASS_BEGIN", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testNestedCharacterClasses2() {  // PY-2908
    doTestHighlighting();
  }

  public void testOctal() {  // PY-2906
    doTestHighlighting();
  }

  public void testBraceInPythonCharacterClass() {  // PY-1929
    doTestHighlighting();
  }

  public void testNegatedBraceInCharacterClass() {
    doTestLexer("[^][]", "CLASS_BEGIN", "CARET", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testDanglingMetacharacters() {  // PY-2430
    doTestHighlighting();
  }

  public void testVerbose() {
    Lexer lexer = new PythonVerboseRegexpParserDefinition().createLexer(LanguageVersionUtil.findDefaultVersion(PythonVerboseRegexpLanguage.INSTANCE));
    PyLexerTestCase.doLexerTest("# abc", lexer, "COMMENT", "COMMENT");
  }

  public void testRedundantEscapeSingleQuote() {  // PY-5027
    doTestHighlighting();
  }

  public void testBraceCommaN() {  // PY-8304
    doTestHighlighting();
  }

  public void testVerboseAsKwArg() {  // PY-8143
    doTestHighlighting();
  }

  public void testVerboseEscapedHash() {  // PY-6545
    doTestHighlighting();
  }

  public void _testDoubleOpenCurly() {  // PY-8252
    doTestHighlighting();
  }

  public void testSingleStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                        "\n" +
                        "re.search('<caret>.*bar',\n" +
                        "          'foobar')\n",
                       ".*bar");
  }

  // PY-11057
  public void testAdjacentStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                        "\n" +
                        "re.search('<caret>.*()'\n" +
                        "          'abc',\n" +
                        "          'foo')\n",
                       ".*()abc");
  }

  public void testParenthesizedStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "re.search((('<caret>foo')), 'foobar')\n",
                       "foo");
  }

  public void testConcatStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "re.search('<caret>(.*' + 'bar)' + 'baz', 'foobar')\n",
                       "(.*bar)baz");
  }

  public void testConcatStringWithValuesRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "def f(x, y):\n" +
                       "    re.search('<caret>.*(' + x + ')' + y, 'foo')\n",
                       ".*(missing)missing");
  }

  public void testPercentFormattingRegexpAutoInjection() {
    doTestInjectedText("import re \n" +
                       "\n" +
                       "def f(x, y):\n" +
                       "    re.search('<caret>.*%s-%d' % (x, y), 'foo')\n",
                       ".*missing-missing");
  }

  public void testNewStyleFormattingRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "def f(x, y):\n" +
                       "    re.search('<caret>.*{foo}-{}'.format(x, foo=y), 'foo')\n",
                       ".*missing-missing");
  }

  public void testNewStyleFormattingEndsWithConstant() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "def f(**kwargs):" +
                       "    re.search('<caret>(foo{bar}baz$)'.format(**kwargs), 'foo')\n",
                       "(foomissingbaz$)");
  }

  private void doTestInjectedText(@Nonnull String text, @Nonnull String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myFixture.getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    assertFalse(files.isEmpty());
    final PsiElement injected = files.get(0).getFirst();
    assertEquals(expected, injected.getText());
  }

  private void doTestHighlighting() {
    myFixture.testHighlighting(true, false, true, "regexp/" + getTestName(true) + ".py");
  }

  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PythonRegexpParserDefinition().createLexer(LanguageVersionUtil.findDefaultVersion(PythonRegexpLanguage.INSTANCE));
    PyLexerTestCase.doLexerTest(text, lexer, expectedTokens);
  }
}
