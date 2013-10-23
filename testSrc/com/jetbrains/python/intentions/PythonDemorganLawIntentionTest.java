/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;

public class PythonDemorganLawIntentionTest extends PyTestCase {
  public void test1() throws Exception {
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.configureByFile("before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention("DeMorgan Law");
    myFixture.launchAction(action);
    myFixture.checkResultByFile("after" + getTestName(false) + ".py");

  }
  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/demorgan";
  }
}