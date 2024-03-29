package com.jetbrains.python.testRunner;

import consulo.process.ExecutionException;
import consulo.process.local.ProcessOutput;
import consulo.util.lang.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.testing.JythonUnitTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 */
public abstract class PyDocTestRunnerTest extends LightPlatformTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public PyDocTestRunnerTest() {
    PyTestCase.initPlatformPrefix();
  }

  public void testEmptySuite() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_empty.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath());
    assertEquals("##teamcity[enteredTheMatrix]", result [0]);
    assertEquals("##teamcity[testCount count='0']", result [1]);
  }

  public void testFile() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath());
    assertEquals(StringUtil.join(result, "\n"), 14, result.length);
    assertEquals("##teamcity[testCount count='3']", result [1]);
    assertTrue(result[2].contains("name='test_file.FirstGoodTest'"));
  }

  private static File getTestDataDir() {
    return new File(PythonTestUtil.getTestDataPath(), "/testRunner/doctests");
  }

  public void testClass() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::FirstGoodTest");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  public void testMethod() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::SecondGoodTest::test_passes");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  public void testFunction() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::factorial");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  private static String[] runUTRunner(String workDir, String... args) throws ExecutionException {
    String helpersPath = PyTestCase.getHelpersPath();
    File utRunner = new File(helpersPath, "pycharm/docrunner.py");
    List<String> allArgs = new ArrayList<String>();
    allArgs.add(utRunner.getPath());
    Collections.addAll(allArgs, args);
    final ProcessOutput output = JythonUnitTestUtil.runJython(workDir, helpersPath, ArrayUtil.toStringArray(allArgs));
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
