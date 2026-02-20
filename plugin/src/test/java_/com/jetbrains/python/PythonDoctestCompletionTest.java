package com.jetbrains.python;

import consulo.language.editor.completion.lookup.LookupElement;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User : ktisha
 */
public abstract class PythonDoctestCompletionTest extends PyTestCase {

  private void doDoctestTest(String expected) {
    String testName = "completion/doctest/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    LookupElement[] elements = myFixture.completeBasic();
    if (elements != null) {
      for (LookupElement lookup : elements) {
        System.out.println(lookup.getLookupString());
        if (lookup.getLookupString().equals(expected))
          return;
      }
    }
    fail();
  }

  public void testForInDoctest() {
    doDoctestTest("for");
  }

  public void testImportInDoctest() {
    doDoctestTest("foo");
  }

  public void testFunctionInDoctest() {
    doDoctestTest("foo");
  }

}
