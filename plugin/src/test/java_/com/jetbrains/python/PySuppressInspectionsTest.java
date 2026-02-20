package com.jetbrains.python;

import java.util.List;

import consulo.language.editor.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.impl.inspections.PyInspection;
import com.jetbrains.python.impl.inspections.PyUnusedLocalInspection;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

/**
 * @author yole
 */
public abstract class PySuppressInspectionsTest extends PyTestCase {
  public void testSuppressedForStatement() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForMethod() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForClass() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedUnusedLocal() {
    doTestHighlighting(PyUnusedLocalInspection.class);
  }

  public void testSuppressForImport() {  // PY-2240
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  private void doTestHighlighting(Class<? extends PyInspection> inspectionClass) {
    myFixture.configureByFile("inspections/suppress/" + getTestName(true) + ".py");
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testSuppressForStatement() {
    myFixture.configureByFile("inspections/suppress/suppressForStatement.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Suppress for statement");
    assertEquals(3, intentions.size());  // Rename reference, Ignore unresolved reference, Mark all unresolved attributes
    IntentionAction suppressAction = intentions.get(0);
    myFixture.launchAction(suppressAction);
    myFixture.checkResultByFile("inspections/suppress/suppressForStatement.after.py");
  }
}
