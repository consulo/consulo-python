package com.jetbrains.python;

import consulo.ide.impl.idea.codeInsight.generation.surroundWith.SurroundWithHandler;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.editor.WriteCommandAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.impl.refactoring.surround.surrounders.statements.PyWithIfSurrounder;
import com.jetbrains.python.impl.refactoring.surround.surrounders.statements.PyWithTryExceptSurrounder;
import com.jetbrains.python.impl.refactoring.surround.surrounders.statements.PyWithWhileSurrounder;

/**
 * @author yole
 */
public abstract class PySurroundWithTest extends PyTestCase {
  public void testSurroundWithIf() throws Exception {
    doTest(new PyWithIfSurrounder());
  }

  public void testSurroundWithWhile() throws Exception {
    doTest(new PyWithWhileSurrounder());
  }

  public void _testSurroundWithTryExcept() throws Exception {
    doTest(new PyWithTryExceptSurrounder());
  }

  private void doTest(final Surrounder surrounder) throws Exception {
    String baseName = "/surround/" + getTestName(false);
    myFixture.configureByFile(baseName + ".py");
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
      }
    }.execute();
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
