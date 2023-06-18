package com.jetbrains.python.inspections;

import javax.annotation.Nonnull;

import com.intellij.testFramework.TestModuleDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author vlan
 */
public abstract class Py3UnresolvedReferencesInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection3K/";

  @Override
  protected TestModuleDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }

  private void doMultiFileTest(@Nonnull final String filename) {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        final String testName = getTestName(false);
        myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
        myFixture.configureFromTempProjectFile(filename);
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }

  public void testNamedTuple() {
    doTest();
  }

  public void testNamedTupleAssignment() {
    doMultiFileTest("a.py");
  }

  // TODO: Currently there are no stubs for namedtuple() in the base classes list and no indicators for forcing stub->AST
  public void _testNamedTupleBaseStub() {
    doMultiFileTest("a.py");
  }

  // PY-10208
  public void testMetaclassMethods() {
    doTest();
  }

  // PY-9011
  public void testDatetimeDateAttributesOutsideClass() {
    doMultiFileTest("a.py");
  }

  public void testObjectNewAttributes() {
    doTest();
  }
}
