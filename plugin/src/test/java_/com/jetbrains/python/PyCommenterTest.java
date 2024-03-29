package com.jetbrains.python;

import consulo.language.editor.action.CodeInsightAction;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.IdeActions;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public abstract class PyCommenterTest extends PyTestCase {
  public void testIndentedComment() {
    doTest();
  }

  public void testUncommentWithoutSpace() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("commenter/" + getTestName(true) + ".py");
    CodeInsightAction action = (CodeInsightAction) ActionManager.getInstance().getAction(IdeActions.ACTION_COMMENT_LINE);
    action.actionPerformedImpl(myFixture.getFile().getProject(), myFixture.getEditor());
    myFixture.checkResultByFile("commenter/" + getTestName(true) + "_after.py", true);

  }
}
