/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.sdk.flavors;

import consulo.annotation.component.ExtensionImpl;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.PythonIcons;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
@ExtensionImpl
public class PyRemoteSdkFlavor extends CPythonSdkFlavor {
  private final static String[] NAMES = new String[]{
    "python",
    "jython",
    "pypy",
    "python.exe",
    "jython.bat",
    "pypy.exe"
  };
  private final static String[] REMOTE_SDK_HOME_PREFIXES = new String[]{
    "ssh:",
    "vagrant:",
    "docker:",
    "docker-compose:"
  };

  public static PyRemoteSdkFlavor INSTANCE = new PyRemoteSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    return List.of();
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return StringUtil.isNotEmpty(path) && checkName(NAMES, getExecutableName(path)) && checkName(REMOTE_SDK_HOME_PREFIXES, path);
  }

  private static boolean checkName(String[] names, @Nullable String name) {
    if (name == null) {
      return false;
    }
    for (String n : names) {
      if (name.startsWith(n)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static String getExecutableName(String path) {
    Path file = Path.of(path);
    return file.getFileName().toString();
  }

  @Override
  public Image getIcon() {
    return PythonIcons.Python.RemoteInterpreter;
  }
}
