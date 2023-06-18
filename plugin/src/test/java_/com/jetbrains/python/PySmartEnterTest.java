package com.jetbrains.python;

import java.util.List;

import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import consulo.language.Language;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.codeEditor.Editor;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author Alexey.Ivanov
 */
public abstract class PySmartEnterTest extends PyTestCase {
  protected static List<SmartEnterProcessor> getSmartProcessors(Language language) {
    return SmartEnterProcessors.INSTANCE.forKey(language);
  }

  public void doTest() {
    myFixture.configureByFile("codeInsight/smartEnter/" + getTestName(true) + ".py");
    final List<SmartEnterProcessor> processors = getSmartProcessors(PythonLanguage.getInstance());
    new WriteCommandAction(myFixture.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final Editor editor = myFixture.getEditor();
        for (SmartEnterProcessor processor : processors) {
          processor.process(myFixture.getProject(), editor, myFixture.getFile());
        }
      }
    }.execute();
    myFixture.checkResultByFile("codeInsight/smartEnter/" + getTestName(true) + "_after.py", true);
  }

  public void testIf() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testElif() {
    doTest();
  }

  public void testForFirst() {
    doTest();
  }

  public void testForSecond() {
    doTest();
  }

  public void testTry() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public void testDocstring() {
    doTest();
  }

  public void testDict() {
    doTest();
  }

  public void testParenthesized() {
    doTest();
  }

  public void testArgumentsFirst() {
    doTest();
  }

  public void testArgumentsSecond() {
    doTest();
  }

  public void testFunc() {
    doTest();
  }

  public void testClass() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testPy891() {
    doTest();
  }

  public void testPy3209() {
    doTest();
  }

  public void testDocRest() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean oldStubOnEnter = codeInsightSettings.JAVADOC_STUB_ON_ENTER;
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.REST);
    try {
      doTest();
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
      codeInsightSettings.JAVADOC_STUB_ON_ENTER = oldStubOnEnter;
    }
  }

  public void testDocEpytext() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean oldStubOnEnter = codeInsightSettings.JAVADOC_STUB_ON_ENTER;
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doTest();
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
      codeInsightSettings.JAVADOC_STUB_ON_ENTER = oldStubOnEnter;

    }
  }

  public void testDocTypeRType() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean oldStubOnEnter = codeInsightSettings.JAVADOC_STUB_ON_ENTER;
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    PyCodeInsightSettings pyCodeInsightSettings = PyCodeInsightSettings.getInstance();
    boolean oldInsertType = pyCodeInsightSettings.INSERT_TYPE_DOCSTUB;
    pyCodeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doTest();
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
      codeInsightSettings.JAVADOC_STUB_ON_ENTER = oldStubOnEnter;
      pyCodeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertType;
    }
  }
}
