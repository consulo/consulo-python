package com.jetbrains.python.refactoring;

import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.parameter.PyIntroduceParameterHandler;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceParameter/")
public abstract class PyIntroduceParameterTest extends PyIntroduceTestCase {
  public void testSimple() {
    doTest();
  }

  public void testReferencedParameter() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testParameter() {
    doTest();
  }

  public void testKwParameter() {
    doTest();
  }

  public void testLastStatement() {
    doTest();
  }

  public void testLocalVariable() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testLocalVariable1() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testLocalVariableParam() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testNonLocal() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"), LanguageLevel.PYTHON32);
  }

  public void testGlobal() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }

  public void testFunctionDef() {
    doTestCannotPerform(PyBundle.message("refactoring.introduce.selection.error"));
  }


  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceParameter";
  }

  protected IntroduceHandler createHandler() {
    return new PyIntroduceParameterHandler();
  }

  private void doTestCannotPerform(String expected) {
    try {
      doTest();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(expected, e.getMessage());
    }
  }

  private void doTestCannotPerform(String expected, LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    try {
      doTestCannotPerform(expected);
    }
    finally {
      setLanguageLevel(null);
    }
  }
}
