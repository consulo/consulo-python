package com.jetbrains.python.intentions;

import consulo.language.editor.intention.IntentionAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NonNls;

/**
 * User: ktisha
 */
public abstract class PyIntentionTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/" + getClass().getSimpleName();
  }

  protected void doTest(String hint, LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doIntentionTest(hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  protected void doIntentionTest(final String hint) {
    final String testFileName = getTestName(true);
    myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doNegativeTest(final String hint) {
    final String testFileName = getTestName(true);
    myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(hint);
    assertNull(intentionAction);
  }
}
