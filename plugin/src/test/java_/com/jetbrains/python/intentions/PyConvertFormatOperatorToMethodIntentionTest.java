package com.jetbrains.python.intentions;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : ktisha
 */
public abstract class PyConvertFormatOperatorToMethodIntentionTest extends PyIntentionTestCase {

  public void testSimple() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testMulti() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testEscaped() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testUnicode() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
}