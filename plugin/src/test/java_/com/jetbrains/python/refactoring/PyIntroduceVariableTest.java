package com.jetbrains.python.refactoring;

import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.impl.refactoring.introduce.variable.PyIntroduceVariableHandler;

import java.util.Collection;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceVariable/")
public abstract class PyIntroduceVariableTest extends PyIntroduceTestCase {
  public void testSimple() {
    doTest();
  }

  public void testPy995() {
    doTest();
  }

  public void testSkipLeadingWhitespace() {  // PY-1338
    doTest();    
  }

  public void testPy2862() {
    doTest();
  }

  public void testMultilineString() {  // PY-4962
    doTest();
  }

  public void testSuggestKeywordArgumentName() {   // PY-1260
    doTestSuggestions(PyExpression.class, "extra_context");
  }

  public void testSuggestArgumentName() {   // PY-1260
    doTestSuggestions(PyExpression.class, "extra_context");
  }

  public void testSuggestTypeName() {  // PY-1336
    doTestSuggestions(PyCallExpression.class, "my_class");
  }

  public void testSuggestStringConstantValue() { // PY-1276
    doTestSuggestions(PyExpression.class, "foo_bar");
  }
  
  public void testDontSuggestBuiltinTypeNames() {  // PY-4474
    final Collection<String> strings = buildSuggestions(PyExpression.class);
    assertTrue(strings.contains("s"));
    assertFalse(strings.contains("str"));
  }
  
  public void testDontSuggestBuiltinTypeNames2() {  // PY-5626
    final Collection<String> strings = buildSuggestions(PyCallExpression.class);
    assertTrue(strings.contains("d"));
    assertFalse(strings.contains("dict"));
  }

  public void testSuggestNamesNotInScope() {  // PY-4605
    final Collection<String> strings = buildSuggestions(PyExpression.class);
    assertTrue(strings.contains("myfunc1"));
    assertFalse(strings.contains("myfunc"));
  }

  public void testIncorrectSelection() {  // PY-4455
    doTestCannotPerform();
  }
  
  public void testOneSidedSelection() {  // PY-4456
    doTestCannotPerform();
  }
  
  public void testFunctionOccurrences() {  // PY-5062
    doTest();
  }

  public void testBackslash() {  // PY-6908
    doTest();
  }

  public void testMultipartString() {  // PY-6698
    doTest();
  }

  // PY-3654
  public void testSimpleSubstring() {
    doTest();
  }

  // PY-3654
  public void testLeftSubstring() {
    doTest();
  }

  // PY-3654
  public void testRightSubstring() {
    doTest();
  }

  // PY-3654
  public void testMiddleSubstring() {
    doTest();
  }

  // PY-3654
  public void testLeftQuoteSubstring() {
    doTest();
  }

  // PY-3654
  public void testSubstringInExpression() {
    doTest();
  }

  // PY-3654
  public void testSubstringInStatement() {
    doTest();
  }

  // PY-3654
  public void testTripleQuotedSubstring() {
    doTest();
  }

  // PY-3654
  public void testSubstringInExpressionStatement() {
    doTest();
  }

  // PY-3654
  public void testBytesSubstring() {
    doTest();
  }

  // PY-3654
  public void testSubstringContainsFormatChars() {
    doTest();
  }

  // PY-3654
  public void testSubstringBreaksFormatChars() {
    doTestCannotPerform();
  }

  // PY-3654
  public void testSubstringContainsEscapes() {
    doTest();
  }

  // PY-3654
  public void testSubstringBreaksEscapes() {
    doTestCannotPerform();
  }

  // PY-3654
  public void testSubstringBeforeFormatTuple() {
    doTest();
  }

  // PY-3654
  public void testSubstringInsideFormatTuple() {
    doTest();
  }

  // PY-3654
  public void testSubstringAfterFormatTuple() {
    doTest();
  }

  // PY-3654
  public void testSubstringAfterFormatTupleWithComma() {
    doTest();
  }

  // PY-3654
  public void testSubstringFromFormatDict() {
    doTest();
  }

  // PY-3654
  public void testSubstringFromFormatSingleValue() {
    doTest();
  }

  // PY-8372
  public void testSubstringBreaksNewStyleFormatChars() {
    doTestCannotPerform();
  }

  // PY-8372
  public void testSubstringNewStylePositional() {
    doTest();
  }

  // PY-8372
  public void testSubstringNewStyleAutomaticNumbering() {
    doTest();
  }

  // PY-8372
  public void testSubstringNewStyleKeywords() {
    doTest();
  }

  // PY-10964
  public void testMultiReference() {
    myFixture.configureByFile(getTestName(true) + ".py");
    boolean inplaceEnabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(true);
      IntroduceHandler handler = createHandler();
      final IntroduceOperation operation = new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a_");
      operation.setReplaceAll(true);
      handler.performAction(operation);
      myFixture.checkResultByFile(getTestName(true) + ".after.py");
    }
    finally {
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(inplaceEnabled);
    }
  }

  private void doTestCannotPerform() {
    boolean thrownExpectedException = false;
    try {
      doTest();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      if (e.getMessage().equals("Cannot perform refactoring using selected element(s)")) {
        thrownExpectedException = true;
      }
    }
    assertTrue(thrownExpectedException);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceVariable";
  }

  protected IntroduceHandler createHandler() {
    return new PyIntroduceVariableHandler();
  }
}
