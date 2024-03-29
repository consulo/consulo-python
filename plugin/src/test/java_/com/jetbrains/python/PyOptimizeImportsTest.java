package com.jetbrains.python;

import consulo.ide.impl.idea.codeInsight.actions.OptimizeImportsAction;
import consulo.dataContext.DataManager;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public abstract class PyOptimizeImportsTest extends PyTestCase {
  public void testSimple() {
    doTest();
  }

  public void testOneOfMultiple() {
    doTest();
  }

  public void testImportStar() {
    doTest();
  }

  public void testImportStarOneOfMultiple() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testFromFuture() {
    doTest();
  }

  public void testUnresolved() {  // PY-2201
    doTest();
  }
  
  public void testSuppressed() {  // PY-5228
    doTest();
  }

  public void testSplit() {
    doTest();
  }

  public void testOrder() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("optimizeImports/" + getTestName(true) + ".py");
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    myFixture.checkResultByFile("optimizeImports/" + getTestName(true) + ".after.py");
  }
}
