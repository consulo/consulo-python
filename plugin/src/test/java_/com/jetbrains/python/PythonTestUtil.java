package com.jetbrains.python;

/**
 * @author yole
 */
public abstract class PythonTestUtil {
  private PythonTestUtil() {
  }

  public static String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData";
  }
}
