package com.jetbrains.python.quickFixes;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

/**
 * User: ktisha
 */
public abstract class AddFieldQuickFixTest extends PyQuickFixTestCase {

  public void testAddClassField() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "FIELD", "A"));
  }

  public void testAddFieldFromMethod() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"));
  }

  public void testAddFieldFromInstance() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"));
  }

  public void testAddFieldAddConstructor() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"));
  }

  public void testAddFieldNewConstructor() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"));
  }

}
