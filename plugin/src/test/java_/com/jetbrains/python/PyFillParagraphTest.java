package com.jetbrains.python;

import consulo.ide.impl.idea.codeInsight.editorActions.fillParagraph.FillParagraphAction;
import consulo.dataContext.DataManager;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnActionEvent;
import consulo.undoRedo.CommandProcessor;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * User : ktisha
 */
public abstract class PyFillParagraphTest extends PyTestCase {

  public void testDocstring() {
    doTest();
  }

  public void testMultilineDocstring() {
    doTest();
  }

  public void testDocstringOneParagraph() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testCommentSecondParagraph() {
    doTest();
  }

  public void testPrefixPostfix() {
    doTest();
  }

  public void testSingleLine() {
    doTest();
  }

  public void testEnter() {
    CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings();
    int oldValue = settings.RIGHT_MARGIN;
    settings.RIGHT_MARGIN = 80;
    try {
      doTest();
    }
    finally {
      settings.RIGHT_MARGIN = oldValue;
    }
  }

  private void doTest() {
    String baseName = "/fillParagraph/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        FillParagraphAction action = new FillParagraphAction();
        action.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "",
                                                 action.getTemplatePresentation(),
                                                 ActionManager.getInstance(), 0));
      }
    }, "", null);
    myFixture.checkResultByFile(baseName + "_after.py", true);
  }
}
