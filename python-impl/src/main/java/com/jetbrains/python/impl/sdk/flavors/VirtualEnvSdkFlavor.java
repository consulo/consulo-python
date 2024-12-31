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

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.SystemInfo;
import consulo.application.util.UserHomeFileUtil;
import consulo.dataContext.DataManager;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.impl.PythonIcons;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User : catherine
 */
@ExtensionImpl
public class VirtualEnvSdkFlavor extends CPythonSdkFlavor {

  private final static String[] NAMES = new String[]{
    "jython",
    "pypy",
    "python.exe",
    "jython.bat",
    "pypy.exe"
  };
  public final static String[] CONDA_DEFAULT_ROOTS = new String[]{
    "anaconda",
    "anaconda3",
    "miniconda",
    "miniconda3",
    "Anaconda",
    "Anaconda3",
    "Miniconda",
    "Miniconda3"
  };

  public static VirtualEnvSdkFlavor INSTANCE = new VirtualEnvSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    final Project project = DataManager.getInstance().getDataContext().getData(CommonDataKeys.PROJECT);
    List<String> candidates = new ArrayList<>();
    if (project != null) {
      VirtualFile rootDir = project.getBaseDir();
      if (rootDir != null) {
        candidates.addAll(findInDirectory(rootDir));
      }
    }

    final VirtualFile path = getDefaultLocation();
    if (path != null) {
      candidates.addAll(findInDirectory(path));
    }

    for (VirtualFile file : getCondaDefaultLocations()) {
      candidates.addAll(findInDirectory(file));
    }

    final VirtualFile pyEnvLocation = getPyEnvDefaultLocations();
    if (pyEnvLocation != null) {
      candidates.addAll(findInDirectory(pyEnvLocation));
    }
    return candidates;
  }

  @Nullable
  public static VirtualFile getPyEnvDefaultLocations() {
    final String path = System.getenv().get("PYENV_ROOT");
    if (!StringUtil.isEmpty(path)) {
      final VirtualFile pyEnvRoot = LocalFileSystem.getInstance().findFileByPath(UserHomeFileUtil.expandUserHome(path).replace('\\', '/'));
      if (pyEnvRoot != null) {
        return pyEnvRoot.findFileByRelativePath("versions");
      }
    }
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));
    if (userHome != null) {
      return userHome.findFileByRelativePath(".pyenv/versions");
    }
    return null;
  }

  public static List<VirtualFile> getCondaDefaultLocations() {
    List<VirtualFile> roots = new ArrayList<>();
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));
    if (userHome != null) {
      for (String root : CONDA_DEFAULT_ROOTS) {
        VirtualFile condaFolder = userHome.findChild(root);
        addEnvsFolder(roots, condaFolder);
        if (SystemInfo.isWindows) {
          final VirtualFile appData = userHome.findFileByRelativePath("AppData\\Local\\Continuum\\" + root);
          addEnvsFolder(roots, appData);
          condaFolder = LocalFileSystem.getInstance().findFileByPath("C:\\" + root);
          addEnvsFolder(roots, condaFolder);
        }
        else {
          final String systemWidePath = "/opt/anaconda";
          condaFolder = LocalFileSystem.getInstance().findFileByPath(systemWidePath);
          addEnvsFolder(roots, condaFolder);
        }
      }
    }
    return roots;
  }

  private static void addEnvsFolder(@Nonnull final List<VirtualFile> roots, @Nullable final VirtualFile condaFolder) {
    if (condaFolder != null) {
      final VirtualFile envs = condaFolder.findChild("envs");
      if (envs != null) {
        roots.add(envs);
      }
    }
  }

  public static VirtualFile getDefaultLocation() {
    final String path = System.getenv().get("WORKON_HOME");
    if (!StringUtil.isEmpty(path)) {
      return LocalFileSystem.getInstance().findFileByPath(UserHomeFileUtil.expandUserHome(path).replace('\\', '/'));
    }

    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));
    if (userHome != null) {
      final VirtualFile predefinedFolder = userHome.findChild(".virtualenvs");
      if (predefinedFolder == null) {
        return userHome;
      }
      return predefinedFolder;
    }
    return null;
  }

  public static Collection<String> findInDirectory(VirtualFile rootDir) {
    List<String> candidates = new ArrayList<>();
    if (rootDir != null) {
      rootDir.refresh(true, false);
      VirtualFile[] suspects = rootDir.getChildren();
      for (VirtualFile child : suspects) {
        if (child.isDirectory()) {
          final VirtualFile bin = child.findChild("bin");
          final VirtualFile scripts = child.findChild("Scripts");
          if (bin != null) {
            final String interpreter = findInterpreter(bin);
            if (interpreter != null) {
              candidates.add(interpreter);
            }
          }
          if (scripts != null) {
            final String interpreter = findInterpreter(scripts);
            if (interpreter != null) {
              candidates.add(interpreter);
            }
          }
        }
      }
    }
    return candidates;
  }

  @Nullable
  private static String findInterpreter(VirtualFile dir) {
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isDirectory()) {
        final String childName = child.getName().toLowerCase();
        for (String name : NAMES) {
          if (SystemInfo.isWindows) {
            if (childName.equals(name)) {
              return FileUtil.toSystemDependentName(child.getPath());
            }
          }
          else {
            if (childName.startsWith(name) || PYTHON_RE.matcher(childName).matches()) {
              if (!childName.endsWith("-config")) {
                return child.getPath();
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public boolean isValidSdkPath(@Nonnull File file) {
    if (!super.isValidSdkPath(file)) {
      return false;
    }
    return PythonSdkType.getVirtualEnvRoot(file.getPath()) != null;
  }

  @Override
  public Image getIcon() {
    return PythonIcons.Python.Virtualenv;
  }
}
