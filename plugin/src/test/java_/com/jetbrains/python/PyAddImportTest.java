package com.jetbrains.python;

import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public abstract class PyAddImportTest extends PyTestCase {
  public void testAddBuiltin() {
    myFixture.configureByFile("addImport/addBuiltin.py");
    new WriteCommandAction(myFixture.getProject(), myFixture.getFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        AddImportHelper.addImportStatement(myFixture.getFile(), "re", null, AddImportHelper.ImportPriority.BUILTIN, null);
      }
    }.execute();
    myFixture.checkResultByFile("addImport/addBuiltin.after.py");
  }

  public void testParens() {  // PY-7400
    doAddImportFrom("urllib", "unquote_plus");
  }

  public void testComment() {  // PY-8034
    doAddImportFrom("urllib", "unquote_plus");
  }

  private void doAddImportFrom(final String path, final String name) {
    myFixture.configureByFile("addImport/" + getTestName(true) + ".py");
    new WriteCommandAction(myFixture.getProject(), myFixture.getFile()) {
      @Override
      protected void run(Result result) throws Throwable {
       // AddImportHelper.addFromImportStatement(myFixture.getFile(), null, path, name, null, AddImportHelper.ImportPriority.BUILTIN, null);
      }
    }.execute();
    myFixture.checkResultByFile("addImport/" + getTestName(true) + ".after.py");
  }
}
