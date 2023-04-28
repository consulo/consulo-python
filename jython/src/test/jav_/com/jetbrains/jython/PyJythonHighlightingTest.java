package com.jetbrains.jython;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.jetbrains.python.inspections.PyCallingNonCallableInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

/**
 * @author yole
 */
public abstract class PyJythonHighlightingTest extends LightCodeInsightFixtureTestCase {
  public void testCallableJavaClass() {
    doCallableTest();
  }

  public void testCallableStaticMethod() {
    doCallableTest();
  }

  private void doCallableTest() {
    myFixture.configureByFile(getTestName(false) + ".py");
    myFixture.enableInspections(PyCallingNonCallableInspection.class, PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }


  @Override
  protected String getTestDataPath() {
    return "/highlighting/jython/";
  }
}
