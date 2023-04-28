package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public abstract class PyFoldingTest extends PyTestCase {
  private void doTest() {
   //TODO [VISTALL] myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(true) + ".py");
  }

  public void testClassTrailingSpace() {  // PY-2544
    doTest();
  }

  public void testDocString() {
    doTest();
  }

  public void testCustomFolding() {
    doTest();
  }

  public void testImportBlock() {
    doTest();
  }
}
