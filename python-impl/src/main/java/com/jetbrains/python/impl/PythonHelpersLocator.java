/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl;

import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginManager;
import consulo.logging.Logger;
import org.jetbrains.annotations.TestOnly;

import java.io.File;

public class PythonHelpersLocator {
  private static final Logger LOG = Logger.getInstance(PythonHelpersLocator.class);

  private PythonHelpersLocator() {
  }

  /**
   * @return the base directory under which various scripts, etc are stored.
   */
  public static File getHelpersRoot() {
    File pluginPath = PluginManager.getPluginPath(PythonHelpersLocator.class);
    return new File(pluginPath, "helpers");
  }

  /**
   * Find a resource by name under helper root.
   * @param resourceName a path relative to helper root
   * @return absolute path of the resource
   */
  public static String getHelperPath(String resourceName) {
    return getHelperFile(resourceName).getAbsolutePath();
  }

  /**
   * Finds a resource file by name under helper root.
   * @param resourceName a path relative to helper root
   * @return a file object pointing to that path; existence is not checked.
   */
  public static File getHelperFile(String resourceName) {
    return new File(getHelpersRoot(), resourceName);
  }

  @TestOnly
  @Deprecated
  public static String getPythonCommunityPath() {
    File pathFromUltimate = new File(ContainerPathManager.get().getHomePath(), "community/python");
    if (pathFromUltimate.exists()) {
      return pathFromUltimate.getPath();
    }
    return new File(ContainerPathManager.get().getHomePath(), "python").getPath();
  }
}
