package com.jetbrains.python;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PySpellCheckerTest extends PyTestCase {
  public void testPlainTextSplitter() {
    doTest();
  }

  public void testPlainTextSplitter2() {
    doTest();
  }

  public void testPlainTextSplitter3() {
    doTest();
  }

  public void testTypoAfterEscapeSequence() {  // PY-4440
    doTest();
  }

  public void testIgnoreEscapeSequence() {  // PY-6794
    doTest();
  }

  private void doTest() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile("inspections/spelling/" + getTestName(true) + ".py");
    myFixture.checkHighlighting(true, false, true);
  }
}
