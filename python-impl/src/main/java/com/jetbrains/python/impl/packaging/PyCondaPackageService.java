/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.packaging;

import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.VirtualEnvSdkFlavor;
import consulo.application.util.SystemInfo;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.process.PathEnvironmentVariableUtil;
import consulo.process.util.ProcessOutput;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.util.lang.VersionComparatorUtil;
import consulo.util.xml.serializer.XmlSerializerUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

@State(name = "PyCondaPackageService", storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/conda_packages.xml"))
public class PyCondaPackageService implements PersistentStateComponent<PyCondaPackageService> {
  private static final Logger LOG = Logger.getInstance(PyCondaPackageService.class);
  public Map<String, String> CONDA_PACKAGES = ContainerUtil.newConcurrentMap();
  public Map<String, List<String>> PACKAGES_TO_RELEASES = new HashMap<>();
  public Set<String> CONDA_CHANNELS = ContainerUtil.newConcurrentSet();

  public long LAST_TIME_CHECKED = 0;

  @Override
  public PyCondaPackageService getState() {
    return this;
  }

  @Override
  public void loadState(PyCondaPackageService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static PyCondaPackageService getInstance() {
    return ServiceManager.getService(PyCondaPackageService.class);
  }

  public Map<String, String> getCondaPackages() {
    return CONDA_PACKAGES;
  }

  public Map<String, String> loadAndGetPackages() {
    if (CONDA_PACKAGES.isEmpty()) {
      updatePackagesCache();
    }
    return CONDA_PACKAGES;
  }

  public Set<String> loadAndGetChannels() {
    if (CONDA_CHANNELS.isEmpty()) {
      updateChannels();
    }
    return CONDA_CHANNELS;
  }

  public void addChannel(@Nonnull final String url) {
    CONDA_CHANNELS.add(url);
  }

  public void removeChannel(@Nonnull final String url) {
    if (CONDA_CHANNELS.contains(url)) {
      CONDA_CHANNELS.remove(url);
    }
  }

  @Nullable
  public static String getCondaPython() {
    final String conda = getSystemCondaExecutable();
    final String pythonName = SystemInfo.isWindows ? "python.exe" : "python";
    if (conda != null) {
      final VirtualFile condaFile = LocalFileSystem.getInstance().findFileByPath(conda);
      if (condaFile != null) {
        final VirtualFile condaDir = condaFile.getParent().getParent();
        final VirtualFile python = condaDir.findChild(pythonName);
        if (python != null) {
          return python.getPath();
        }
      }
    }
    return getCondaExecutable(pythonName);
  }

  @Nullable
  public static String getSystemCondaExecutable() {
    final String condaName = SystemInfo.isWindows ? "conda.exe" : "conda";
    final File condaInPath = PathEnvironmentVariableUtil.findInPath(condaName);
    if (condaInPath != null) {
      return condaInPath.getPath();
    }
    return getCondaExecutable(condaName);
  }

  @Nullable
  public static String getCondaExecutable(VirtualFile sdkPath) {
    final VirtualFile bin = sdkPath.getParent();
    final String condaName = SystemInfo.isWindows ? "conda.exe" : "conda";
    final VirtualFile conda = bin.findChild(condaName);
    if (conda != null) {
      return conda.getPath();
    }
    return getSystemCondaExecutable();
  }

  @Nullable
  public static String getCondaExecutable(@Nonnull final String condaName) {
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));
    if (userHome != null) {
      for (String root : VirtualEnvSdkFlavor.CONDA_DEFAULT_ROOTS) {
        VirtualFile condaFolder = userHome.findChild(root);
        String executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) {
          return executableFile;
        }
        if (SystemInfo.isWindows) {
          condaFolder = LocalFileSystem.getInstance().findFileByPath("C:\\" + root);
          executableFile = findExecutable(condaName, condaFolder);
          if (executableFile != null) {
            return executableFile;
          }
        }
        else {
          final String systemWidePath = "/opt/anaconda";
          condaFolder = LocalFileSystem.getInstance().findFileByPath(systemWidePath);
          executableFile = findExecutable(condaName, condaFolder);
          if (executableFile != null) {
            return executableFile;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static String findExecutable(String condaName, @Nullable final VirtualFile condaFolder) {
    if (condaFolder != null) {
      final VirtualFile bin = condaFolder.findChild(SystemInfo.isWindows ? "Scripts" : "bin");
      if (bin != null) {
        String directoryPath = bin.getPath();
        if (!SystemInfo.isWindows) {
          final VirtualFile[] children = bin.getChildren();
          if (children.length == 0) {
            return null;
          }
          directoryPath = children[0].getPath();
        }
        final String executableFile = PythonSdkType.getExecutablePath(directoryPath, condaName);
        if (executableFile != null) {
          return executableFile;
        }
      }
    }
    return null;
  }

  public void updatePackagesCache() {
    final String condaPython = getCondaPython();
    if (condaPython == null) {
      LOG.warn("Could not find conda python interpreter");
      return;
    }
    final String path = PythonHelpersLocator.getHelperPath("conda_packaging_tool.py");
    final String runDirectory = new File(condaPython).getParent();
    final String[] command = {
      condaPython,
      path,
      "listall"
    };
    final ProcessOutput output = PySdkUtil.getProcessOutput(runDirectory, command);
    if (output.getExitCode() != 0) {
      LOG.warn("Failed to get list of conda packages");
      LOG.warn(StringUtil.join(command, " "));
      return;
    }
    final List<String> lines = output.getStdoutLines();
    for (String line : lines) {
      final List<String> split = StringUtil.split(line, "\t");
      if (split.size() < 2) {
        continue;
      }
      final String aPackage = CONDA_PACKAGES.get(split.get(0));
      if (aPackage != null) {
        if (VersionComparatorUtil.compare(aPackage, split.get(1)) < 0) {
          CONDA_PACKAGES.put(split.get(0), split.get(1));
        }
      }
      else {
        CONDA_PACKAGES.put(split.get(0), split.get(1));
      }

      if (PACKAGES_TO_RELEASES.containsKey(split.get(0))) {
        final List<String> versions = PACKAGES_TO_RELEASES.get(split.get(0));
        if (!versions.contains(split.get(1))) {
          versions.add(split.get(1));
        }
      }
      else {
        final ArrayList<String> versions = new ArrayList<>();
        versions.add(split.get(1));
        PACKAGES_TO_RELEASES.put(split.get(0), versions);
      }
    }
    LAST_TIME_CHECKED = System.currentTimeMillis();
  }

  @Nonnull
  public List<String> getPackageVersions(@Nonnull final String packageName) {
    if (PACKAGES_TO_RELEASES.containsKey(packageName)) {
      return PACKAGES_TO_RELEASES.get(packageName);
    }
    return Collections.emptyList();
  }

  public void updateChannels() {
    final String condaPython = getCondaPython();
    if (condaPython == null) {
      return;
    }
    final String path = PythonHelpersLocator.getHelperPath("conda_packaging_tool.py");
    final String runDirectory = new File(condaPython).getParent();
    final ProcessOutput output = PySdkUtil.getProcessOutput(runDirectory, new String[]{
      condaPython,
      path,
      "channels"
    });
    if (output.getExitCode() != 0) {
      return;
    }
    final List<String> lines = output.getStdoutLines();
    for (String line : lines) {
      CONDA_CHANNELS.add(line);
    }
    LAST_TIME_CHECKED = System.currentTimeMillis();
  }
}
