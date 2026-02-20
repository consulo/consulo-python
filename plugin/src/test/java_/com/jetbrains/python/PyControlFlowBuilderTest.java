package com.jetbrains.python;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import junit.framework.Assert;

import java.io.IOException;

/**
 * @author oleg
 */
public abstract class PyControlFlowBuilderTest extends LightMarkedTestCase {

  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/codeInsight/controlflow/";
  }

  private void doTest() {
    String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    consulo.ide.impl.idea.codeInsight.controlflow.ControlFlow flow = ControlFlowCache.getControlFlow((PyFile)myFile);
    String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
   }

  public void testAssert() {
    doTest();
  }

  public void testAssertFalse() {
    doTest();
  }

  public void testFile() {
    doTest();
  }

  public void testIf() {
    doTest();
  }

  public void testFor() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testBreak() {
    doTest();
  }

  public void testContinue() {
    doTest();
  }

  public void testReturn() {
    doTest();
  }

  public void testTry() {
    doTest();
  }

  public void testImport() {
    doTest();
  }

  public void testListComp() {
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  public void testAssignment2() {
    doTest();
  }

  public void testAugAssignment() {
    doTest();
  }

  public void testSliceAssignment() {
    doTest();
  }

  public void testIfElseReturn() {
    doTest();
  }

  public void testRaise() {
    doTest();
  }

  public void testReturnFor() {
    doTest();
  }

  public void testForIf() {
    doTest();
  }

  public void testForReturn() {
    doTest();
  }

  public void testForTryContinue() {
    doTest();
  }

  public void testTryRaiseFinally() {
    doTest();
  }

  public void testTryExceptElseFinally() {
    doTest();
  }

  public void testTryFinally() {
    doTest();
  }

  public void testDoubleTry() {
    doTest();
  }

  public void testTryTry() {
    doTest();
  }

  public void testIsinstance() {
    doTest();
  }

  public void testLambda() {
    doTest();
  }

  public void testManyIfs() {
    doTest();
  }
  
  public void testSuperclass() {
    doTest();
  }
  
  public void testDefaultParameterValue() {
    doTest();
  }

  public void testLambdaDefaultParameter() {
    doTest();
  }
  
  public void testDecorator() {
    doTestFirstStatement();
  }

  public void testSetComprehension() {
    doTest();
  }
  
  public void testTypeAnnotations() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testQualifiedSelfReference() {
    String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    String fullPath = getTestDataPath() + testName + ".txt";
    PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  public void testSelf() {
    String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    String fullPath = getTestDataPath() + testName + ".txt";
    PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    consulo.ide.impl.idea.codeInsight.controlflow.ControlFlow flow = ControlFlowCache.getControlFlow(pyClass.getMethods()[0]);
    check(fullPath, flow);
  }

  public void testTryBreak() {
    String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    ControlFlow flow = ControlFlowCache.getControlFlow((PyFunction)((PyFile)myFile).getStatements().get(0));
    String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
  }

  public void testFunction() {
    doTestFirstStatement();
  }

  // PY-7784
  public void testAssertFalseArgument() {
    doTest();
  }

  public void testConditionalExpression() {
    doTest();
  }

  private void doTestFirstStatement() {
    String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    String fullPath = getTestDataPath() + testName + ".txt";
    ControlFlow flow = ControlFlowCache.getControlFlow((ScopeOwner)((PyFile)myFile).getStatements().get(0));
    check(fullPath, flow);
  }

  private static void check(String fullPath, ControlFlow flow) {
    StringBuffer buffer = new StringBuffer();
    Instruction[] instructions = flow.getInstructions();
    for (Instruction instruction : instructions) {
      buffer.append(instruction).append("\n");
    }
    VirtualFile vFile = PyTestCase.getVirtualFileByName(fullPath);
    try {
      String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
      Assert.assertEquals(fileText.trim(), buffer.toString().trim());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
