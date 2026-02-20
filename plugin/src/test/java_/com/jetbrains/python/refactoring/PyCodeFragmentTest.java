package com.jetbrains.python.refactoring;

import consulo.ide.impl.idea.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import consulo.ide.impl.idea.codeInsight.codeFragment.CodeFragment;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyFile;

import java.util.TreeSet;

/**
 * @author oleg
 */
public abstract class PyCodeFragmentTest extends LightMarkedTestCase {
  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/codeInsight/codefragment/";
  }

  final private String BEGIN_MARKER = "<begin>";
  final private String END_MARKER = "<end>";
  final private String RESULT_MARKER = "<result>";

  private void doTest(Pair<String, String>... files2Create) throws Exception {
    String testName = getTestName(false).toLowerCase();
    String fullPath = getTestDataPath() + testName + ".test";

    VirtualFile vFile = getVirtualFileByName(fullPath);
    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");

    int beginMarker = fileText.indexOf(BEGIN_MARKER);
    int endMarker = fileText.indexOf(END_MARKER);
    int resultMarker = fileText.indexOf(RESULT_MARKER);
    assertTrue(beginMarker != -1);
    assertTrue(endMarker != -1);
    assertTrue(resultMarker != -1);

    StringBuilder builder = new StringBuilder();
    builder.append(fileText.substring(0, beginMarker));
    builder.append(fileText.substring(beginMarker + BEGIN_MARKER.length(), endMarker));
    builder.append((fileText.substring(endMarker + END_MARKER.length(), resultMarker)));

    String result = fileText.substring(resultMarker + RESULT_MARKER.length());

    // Create additional files
    for (Pair<String, String> pair : files2Create) {
      myFixture.addFileToProject(pair.first, pair.second);
    }

    PyFile file = (PyFile)myFixture.addFileToProject(testName + ".py", builder.toString());
    check(file, beginMarker, endMarker, result);
  }

  private void check(PyFile myFile, int beginMarker, int endMarker, String result) {
    PsiElement startElement = myFile.findElementAt(beginMarker);
    PsiElement endElement = myFile.findElementAt(endMarker - BEGIN_MARKER.length());
    PsiElement context = PsiTreeUtil.findCommonParent(startElement, endElement);
    if (!(context instanceof ScopeOwner)) {
      context = PsiTreeUtil.getParentOfType(context, ScopeOwner.class);
    }
    StringBuffer buffer = new StringBuffer();
    try {
      CodeFragment fragment = PyCodeFragmentUtil.createCodeFragment((ScopeOwner)context, startElement, endElement);
      if (fragment.isReturnInstructionInside()) {
        buffer.append("Return instruction inside found").append("\n");
      }
      buffer.append("In:\n");
      for (String inputVariable : new TreeSet<String>(fragment.getInputVariables())) {
        buffer.append(inputVariable).append('\n');
      }
      buffer.append("Out:\n");
      for (String outputVariable : new TreeSet<String>(fragment.getOutputVariables())) {
        buffer.append(outputVariable).append('\n');
      }
    }
    catch (consulo.ide.impl.idea.codeInsight.codeFragment.CannotCreateCodeFragmentException e) {
      assertEquals(result.trim(), e.getMessage());
      return;
    }
    assertEquals(result.trim(), buffer.toString().trim());
  }

  public void testImportBefore() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }

  public void testImportBeforeUseInside() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }

  public void testImportInsideUseAfter() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }

  public void testImportAfter() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }


  public void testSimple() throws Exception {
    doTest();
  }

  public void testWhile() throws Exception {
    doTest();
  }

  public void testEmpty() throws Exception {
    doTest();
  }

  public void testOut() throws Exception {
    doTest();
  }


  public void testExpression() throws Exception {
    doTest();
  }

  public void testParameters() throws Exception {
    doTest();
  }

  public void testVariables() throws Exception {
    doTest();
  }

  public void testVariablesEmptyOut() throws Exception {
    doTest();
  }

  public void testVariablesEmptyIn() throws Exception {
    doTest();
  }

  public void testExpression2() throws Exception {
    doTest();
  }

  public void testAugAssignment() throws Exception {
    doTest();
  }

  public void testClass() throws Exception {
    doTest();
  }

  public void testForIfReturn() throws Exception {
    doTest();
  }

  public void testRaise2102() throws Exception {
    doTest();
  }

}

