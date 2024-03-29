package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.impl.inspections.PyTypeCheckerInspection;

/**
 * @author vlan
 */
public abstract class PyTypeCheckerInspectionTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyTypeCheckerInspection/";

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
    myFixture.enableInspections(PyTypeCheckerInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doMultiFileTest() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
    myFixture.configureFromTempProjectFile("a.py");
    myFixture.enableInspections(PyTypeCheckerInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testSimple() {
    doTest();
  }

  public void testStrUnicode() {
    doTest();
  }

  public void testListTuple() {
    doTest();
  }

  public void testBuiltinNumeric() {
    doTest();
  }

  public void testGenerator() {
    doTest();
  }

  // PY-4025
  public void testFunctionAssignments() {
    doTest();
  }

  public void testOldStyleClasses() {
    doTest();
  }

  public void testPartlyUnknownType() {
    doTest();
  }

  public void testTypeAssertions() {
    doTest();
  }

  public void testLocalTypeResolve() {
    doTest();
  }

  public void testSubscript() {
    doTest();
  }

  public void testComparisonOperators() {
    doTest();
  }

  public void testRightOperators() {
    doTest();
  }

  public void testStringInteger() {
    doTest();
  }

  public void testIsInstanceImplicitSelfTypes() {
    doTest();
  }

  public void testNotNone() {
    doTest();
  }

  public void testBuiltinFunctions() {
    doTest();
  }

  public void testUnionReturnTypes() {
    doTest();
  }

  public void testEnumerateIterator() {
    doTest();
  }

  public void testGenericUserFunctions() {
    doTest();
  }

  public void testGenericUserClasses() {
    doTest();
  }

  public void testDictGenerics() {
    doTest();
  }

  // PY-5474
  public void testBadSubscriptExpression() {
    doTest();
  }

  // PY-5873
  public void testTypeOfRaiseException() {
    doTest();
  }

  // PY-6542
  public void testDictLiterals() {
    doTest();
  }

  // PY-6570
  public void testDictLiteralIndexing() {
    doTest();
  }

  // PY-6606
  public void testBuiltinBaseClass() {
    doTest();
  }

  // PY-6803
  public void testPropertyAndFactoryFunction() {
    doTest();
  }

  // PY-7179
  public void testDecoratedFunction() {
    doTest();
  }

  // PY-6925
  public void testAssignedOperator() {
    doTest();
  }

  // PY-7244
  public void testGenericArguments() {
    doTest();
  }

  // PY-7757
  public void testOpenRead2K() {
    doTest();
  }

  // PY-8182
  public void testUnionWithSameMethods() {
    doTest();
  }

  // PY-8181
  public void testBytesSubclassAsStr() {
    doTest();
  }

  // PY-9118
  public void testNegativeIsInstance() {
    doTest();
  }

  // PY-7340
  public void testFieldWithNoneInStub() {
    doMultiFileTest();
  }

  public void testBoundedGeneric() {
    doTest();
  }

  public void testNotImportedClassInDocString() {
    doMultiFileTest();
  }

  // PY-6728
  public void testForLoopIteration() {
    doTest();
  }

  // PY-4285
  public void testMapReturnElementType() {
    doTest();
  }

  // PY-10413
  public void testFunctionParameterReturnType() {
    doTest();
  }

  // PY-10095
  public void testStringStartsWith() {
    doTest();
  }

  // PY-10854
  public void testSecondFormIter() {
    doTest();
  }
}
