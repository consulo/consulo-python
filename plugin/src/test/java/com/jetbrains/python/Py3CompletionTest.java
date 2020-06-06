package com.jetbrains.python;

import java.util.List;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.testFramework.TestModuleDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public abstract class Py3CompletionTest extends PyTestCase {
  @Override
  protected TestModuleDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  public void testPropertyDecorator() {
    doTest();
  }

  public void testPropertyAfterAccessor() {  // PY-5951
    doTest();
  }

  public void testNamedTuple() {
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    final List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertTrue(strings.contains("lat"));
    assertTrue(strings.contains("long"));
  }

  public void testNamedTupleBaseClass() {
    doTest();
  }

  private void doTest() {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }
}
