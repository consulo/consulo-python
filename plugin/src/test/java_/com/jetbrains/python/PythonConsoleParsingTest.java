package com.jetbrains.python;

import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.testFramework.ParsingTestCase;

/**
 * @author traff
 */
@TestDataPath("$CONTENT_ROOT/../testData/ipython/")
public abstract class PythonConsoleParsingTest extends ParsingTestCase
{
  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public PythonConsoleParsingTest() {
    super("psi", "py"/*, new PythonParserDefinition()*/);
    PyTestCase.initPlatformPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  public void testQuestionEnd() {
    PsiFile psiFile = consoleFile("sys?");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testDoubleQuestionEnd() {
    PsiFile psiFile = consoleFile("sys??");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testQuestionStart() {
    PsiFile psiFile = consoleFile("?sys");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testDoubleQuestionStart() {
    PsiFile psiFile = consoleFile("??sys");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testSlashGlobals() {
    PsiFile psiFile = consoleFile("/globals");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testComma() {
    PsiFile psiFile = consoleFile(", call while True");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testSemicolon() {
    PsiFile psiFile = consoleFile("; length str");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testCallNoAutomagic() {
    PsiFile psiFile = consoleFile("; length str");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }


  public void doTest(LanguageLevel languageLevel) {
    LanguageLevel prev = myLanguageLevel;
    myLanguageLevel = languageLevel;
    try {
      doTest(true);
    }
    finally {
      myLanguageLevel = prev;
    }
  }

  private PsiFile consoleFile(String text) {
    return createPsiFile("Console.py", text);
  }

  /*@Override
  protected PsiFile createFile(String name, String text) {
    LightVirtualFile originalFile = new LightVirtualFile(name, myLanguage, text);
    LightVirtualFile virtualFile = new LightVirtualFile(name, myLanguage, text);
    virtualFile.setOriginalFile(originalFile);

    originalFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    originalFile.putUserData(LanguageLevel.KEY, myLanguageLevel);
    PyConsoleUtil.markIPython(originalFile);
    return createFile(virtualFile);
  }  */
}

