package com.jetbrains.rest.completion;

import consulo.language.editor.completion.lookup.LookupElement;
import com.jetbrains.rest.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public abstract class RestRefereneceCompletionTest extends RestFixtureTestCase {

  public void testFootnote() {
    doTest();
  }

  public void testCitation() {
    doTest();
  }

  public void testHyperlink() {
    doTest();
  }

  public void testAnonimHyperlink() {
    doTest();
  }

  public void testSubstitution() {
    doTest();
  }

  public void testUnderscore() {
    doTest();
  }

  public void testAlreadyFilled() {
    final String path = "/completion/reference/alreadyFilled.rst";
    myFixture.configureByFile(path);
    LookupElement[] lookups = myFixture.completeBasic();
    assertNotNull(lookups);
    assertEquals(0, lookups.length);
  }

  private void doTest() {
    final String path = "/completion/reference/" + getTestName(true);
    myFixture.configureByFile(path + ".rst");
    myFixture.completeBasic();
    myFixture.checkResultByFile(path + ".after.rst");
  }
}
