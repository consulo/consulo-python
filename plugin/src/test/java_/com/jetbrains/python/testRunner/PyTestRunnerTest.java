package com.jetbrains.python.testRunner;

import consulo.process.ExecutionException;
import consulo.process.local.ProcessOutput;
import consulo.util.lang.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.testing.JythonUnitTestUtil;
import consulo.container.boot.ContainerPathManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public abstract class PyTestRunnerTest extends LightPlatformTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public PyTestRunnerTest() {
    PyTestCase.initPlatformPrefix();
  }

  public void testEmptySuite() throws ExecutionException {
    String[] result = runUTRunner(ContainerPathManager.get().getHomePath(), "true");
    assertEquals("##teamcity[testCount count='0']", result [1]);
  }

  public void testFile() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath(), "true");
    assertEquals(StringUtil.join(result, "\n"), 11, result.length);
    assertEquals("##teamcity[enteredTheMatrix]", result [0]);
    assertEquals("##teamcity[testCount count='2']", result [1]);
    assertTrue(result[2].endsWith("test1.BadTest' name='test1.BadTest']"));
    assertTrue(result[3].endsWith("test1.BadTest.test_fails' name='test_fails']"));
    assertTrue(result [4], result[4].startsWith("##teamcity[testFailed") && result [4].contains("name='test_fails'"));
  }

  private static File getTestDataDir() {
    return new File(PythonTestUtil.getTestDataPath(), "/testRunner/tests");
  }

  public void testClass() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest", "true");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  public void testMethod() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest::test_passes", "true");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  public void testFolder() throws ExecutionException {
    final File testDir = getTestDataDir();
    String[] result = runUTRunner(testDir.getPath(), testDir.getPath() + "/", "true");
    assertEquals(StringUtil.join(result, "\n"), 15, result.length);
  }

  public void testDependent() throws ExecutionException {
    final File testDir = new File(PythonTestUtil.getTestDataPath(), "testRunner");
    String[] result = runUTRunner(testDir.getPath(), new File(testDir, "dependentTests/test_my_class.py").getPath(), "true");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  private static String[] runUTRunner(String workDir, String... args) throws ExecutionException {
    File helpersDir = new File(PyTestCase.getHelpersPath());
    File utRunner = new File(helpersDir, "pycharm/utrunner.py");
    List<String> allArgs = new ArrayList<String>();
    allArgs.add(utRunner.getPath());
    Collections.addAll(allArgs, args);
    final ProcessOutput output = JythonUnitTestUtil.runJython(workDir, helpersDir.getPath(), ArrayUtil.toStringArray(allArgs));
    assertEquals(output.getStderr(), 0, splitLines(output.getStderr()).length);
    return splitLines(output.getStdout());
  }

  private static String[] splitLines(final String out) {
    List<String> result = new ArrayList<String>();
    final String[] lines = StringUtil.convertLineSeparators(out).split("\n");
    for (String line : lines) {
      if (line.length() > 0 && !line.contains("*sys-package-mgr*")) {
        result.add(line);
      }
    }
    return ArrayUtil.toStringArray(result);
  }
}
