/*
 * Copyright 2013-2016 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.run;

import consulo.application.util.SystemInfo;
import consulo.logging.Logger;
import consulo.process.local.EnvironmentUtil;
import consulo.util.io.FileUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PyVirtualEnvReader extends EnvironmentUtil.ShellEnvReader {
  private static final Logger LOG = Logger.getInstance(PyVirtualEnvReader.class);

  private String activate;

  public PyVirtualEnvReader(String virtualEnvSdkPath) {
    try {
      activate = findActivateScript(virtualEnvSdkPath, getShell());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String findActivateScript(String path, String shellPath) {
    String shellName = shellPath != null ? new File(shellPath).getName() : null;

    File activate;
    if (SystemInfo.isWindows) {
      activate = new File(new File(path).getParentFile(), "activate.bat");
    }
    else {
      activate = ("fish".equals(shellName) || "csh".equals(shellName)) ? new File(new File(path).getParentFile(),
                                                                                  "activate." + shellName) : new File(new File(path).getParentFile(),
                                                                                                                      "activate");
    }

    return activate.exists() ? activate.getAbsolutePath() : null;
  }

  public String getActivate() {
    return activate;
  }

  @Nullable
  @Override
  protected String getShell() {
    if (new File("/bin/bash").exists()) {
      return "/bin/bash";
    }
    else if (new File("/bin/sh").exists()) {
      return "/bin/sh";
    }
    else {
      return super.getShell();
    }
  }

  @Override
  protected List<String> getShellProcessCommand() {
    String shellPath = getShell();

    if (shellPath == null || !new File(shellPath).canExecute()) {
      throw new IllegalArgumentException("shell:" + shellPath);
    }

    return activate != null ? Arrays.asList(shellPath, "-c", ". '$activate'") : super.getShellProcessCommand();
  }

  public Map<String, String> readPythonEnv() throws Exception {
    if (SystemInfo.isUnix) {
      // pass shell environment for correct virtualenv environment setup (virtualenv expects to be executed from the terminal)
      return super.readShellEnv(null, EnvironmentUtil.getEnvironmentMap());
    }
    else {
      if (activate != null) {
        return readVirtualEnvOnWindows(activate);
      }
      else {
        LOG.error("Can't find activate script for $virtualEnvSdkPath");
        return Collections.emptyMap();
      }
    }
  }

  private Map<String, String> readVirtualEnvOnWindows(String activate) throws Exception {
    File activateFile = FileUtil.createTempFile("pycharm-virualenv-activate.", ".bat", false);
    File envFile = FileUtil.createTempFile("pycharm-virualenv-envs.", ".tmp", false);
    try {
      consulo.ide.impl.idea.openapi.util.io.FileUtil.copy(new File(activate), activateFile);
      consulo.ide.impl.idea.openapi.util.io.FileUtil.appendToFile(activateFile, "\n\nset");
      List<String> command = Arrays.asList(activateFile.getPath(), ">", envFile.getAbsolutePath());

      return runProcessAndReadOutputAndEnvs(command, null, envFile.toPath()).getValue();
    }
    finally {
      FileUtil.delete(activateFile);
      FileUtil.delete(envFile);
    }
  }
}
