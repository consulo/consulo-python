package com.jetbrains.python;

import consulo.language.editor.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.Arrays;

/**
 * User: ktisha
 */
public abstract class PyQuickFixTestCase extends PyTestCase {
  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/quickFixes/" + getClass().getSimpleName();
  }

  protected void doQuickFixTest(Class inspectionClass, String hint) {
    String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
    IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doInspectionTest(Class inspectionClass) {
    String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doMultifilesTest(Class inspectionClass, String hint, String[] files) {
    String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    String [] filenames = Arrays.copyOf(files, files.length + 1);

    filenames[files.length] = testFileName + ".py";
    myFixture.configureByFiles(filenames);
    IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + ".py", testFileName + "_after.py", true);
  }
}
