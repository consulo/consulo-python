package com.jetbrains.rest.completion;

import consulo.language.editor.completion.lookup.LookupElement;
import com.jetbrains.rest.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public abstract class RestOptionCompletionTest extends RestFixtureTestCase {

  public void testFootnote() {
    final String filePath = "/completion/option/footnote.rst";
    myFixture.configureByFiles(filePath);
    final LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(0, items.length);
  }

  public void testImage() {
    doTest();
  }

  public void testOutsideDirective() {
    final String filePath = "/completion/option/outside.rst";
    myFixture.configureByFiles(filePath);
    final LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(0, items.length);
  }

  private void doTest() {
    final String path = "/completion/option/" + getTestName(true);
    myFixture.configureByFile(path + ".rst");
    myFixture.completeBasic();
    myFixture.checkResultByFile(path + ".after.rst");
  }
}
