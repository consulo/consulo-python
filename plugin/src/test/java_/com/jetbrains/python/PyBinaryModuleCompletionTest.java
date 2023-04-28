package com.jetbrains.python;

import com.intellij.testFramework.TestModuleDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public abstract class PyBinaryModuleCompletionTest extends PyTestCase {
  public void testPySideImport() {  // PY-2443
    myFixture.configureByFile("completion/pySideImport.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/pySideImport.after.py");
  }

  @Override
  protected TestModuleDescriptor getProjectDescriptor() {
    return ourDescriptor;
  }

  private static PyLightProjectDescriptor ourDescriptor = new PyLightProjectDescriptor("WithBinaryModules");
}
