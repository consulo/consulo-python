package com.jetbrains.python.fixtures;

/**
 * @author yole
 */
public abstract class PyProfessionalTestCase extends PyTestCase {
  @Override
  protected String getTestDataPath() {
    return getProfessionalTestDataPath();
  }

  public static String getProfessionalTestDataPath() {
    return "";
  }
}
