package com.jetbrains.rest.completion;

import consulo.language.editor.completion.CamelHumpMatcher;
import consulo.language.editor.completion.lookup.LookupElement;
import com.jetbrains.rest.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public abstract class RestDirectiveCompletionTest extends RestFixtureTestCase {

  public void testNote() {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    doTest();
  }

  public void testInline() {
    String filePath = "/completion/directive/inline.rst";
    myFixture.configureByFiles(filePath);
    LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(0, items.length);
  }

  public void testMultiple() {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    String filePath = "/completion/directive/multiple.rst";
    myFixture.configureByFiles(filePath);
    LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(7, items.length);

    assertEquals("caution::", items[0].getLookupString());
  }

  private void doTest() {
    String path = "/completion/directive/" + getTestName(true);
    myFixture.configureByFile(path + ".rst");
    myFixture.completeBasic();
    myFixture.checkResultByFile(path + ".after.rst");
  }
}
