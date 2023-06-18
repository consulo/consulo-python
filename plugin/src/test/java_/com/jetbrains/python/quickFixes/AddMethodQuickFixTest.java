package com.jetbrains.python.quickFixes;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.impl.inspections.PyClassHasNoInitInspection;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

/**
 * User: ktisha
 */
public abstract class AddMethodQuickFixTest extends PyQuickFixTestCase {

  public void testAddInit() {
    doQuickFixTest(PyClassHasNoInitInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "__init__", "A"));
  }

  public void testAddInitAfterDocstring() {
    doQuickFixTest(PyClassHasNoInitInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "__init__", "A"));
  }

  public void testAddMethodReplacePass() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"));
  }

  public void testAddMethodFromInstance() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"));
  }

  public void testAddMethodFromMethod() {
    doQuickFixTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"));
  }

}
