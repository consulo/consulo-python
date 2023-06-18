package com.jetbrains.python;

import com.jetbrains.python.impl.PythonHelpersLocator;

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
