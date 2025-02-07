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

package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.psi.search.GlobalSearchScopes;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author yole
 */
public class PyProjectScopeBuilder {
   /**
   * Calculates a search scope which excludes Python standard library tests. Using such scope may be quite a bit slower than using
   * the regular "project and libraries" search scope, so it should be used only for displaying the list of variants to the user
   * (for example, for class name completion or auto-import).
   *
   * @param project the project for which the scope should be calculated
   * @return the resulting scope
   */
  public static GlobalSearchScope excludeSdkTestsScope(Project project) {
    final Sdk sdk = null;
    // TODO cache the scope in project userdata (update when SDK paths change or different project SDK is selected)
    GlobalSearchScope scope = excludeSdkTestsScope(project, sdk);
    return scope != null ? (GlobalSearchScope) ProjectScopes.getAllScope(project).intersectWith(scope) : (GlobalSearchScope) ProjectScopes.getAllScope(project);
  }

  public static GlobalSearchScope excludeSdkTestsScope(PsiElement anchor) {
    final Project project = anchor.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(anchor);
    if (module != null) {
      Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null) {
        GlobalSearchScope excludeScope = excludeSdkTestsScope(project, sdk);
        if (excludeScope != null) {
          return GlobalSearchScope.allScope(project).intersectWith(excludeScope);
        }
      }
    }
    return excludeSdkTestsScope(project);
  }

  @Nullable
  public static GlobalSearchScope excludeSdkTestsScope(Project project, Sdk sdk) {
    if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
      VirtualFile libDir = findLibDir(sdk);
      if (libDir != null) {
        // superset of test dirs found in Python 2.5 to 3.1
        List<VirtualFile> testDirs = findTestDirs(libDir, "test", "bsddb/test", "ctypes/test", "distutils/tests", "email/test",
                                                  "importlib/test", "json/tests", "lib2to3/tests", "sqlite3/test", "tkinter/test",
                                                  "idlelib/testcode.py");
        if (!testDirs.isEmpty()) {
          GlobalSearchScope scope = buildUnionScope(project, testDirs);
          return GlobalSearchScope.notScope(scope);
        }
      }
    }
    return null;
  }

  private static GlobalSearchScope buildUnionScope(Project project, List<VirtualFile> testDirs) {
    GlobalSearchScope scope = GlobalSearchScopes.directoryScope(project, testDirs.get(0), true);
    for (int i = 1; i < testDirs.size(); i++) {
      scope = scope.union(GlobalSearchScopes.directoryScope(project, testDirs.get(i), true));
    }
    return scope;
  }

  private static List<VirtualFile> findTestDirs(VirtualFile baseDir, String... relativePaths) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String path : relativePaths) {
      VirtualFile child = baseDir.findFileByRelativePath(path);
      if (child != null) {
        result.add(child);
      }
    }
    return result;
  }

  @Nullable
  public static VirtualFile findLibDir(Sdk sdk) {
    return findLibDir(sdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance()));
  }

  public static VirtualFile findVirtualEnvLibDir(Sdk sdk) {
    VirtualFile[] classVFiles = sdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance());
    String homePath = sdk.getHomePath();
    if (homePath != null) {
      File root = PythonSdkType.getVirtualEnvRoot(homePath);
      if (root != null) {
        File libRoot = new File(root, "lib");
        File[] versionRoots = libRoot.listFiles();
        if (versionRoots != null && versionRoots.length == 1) {
          libRoot = versionRoots[0];
        }
        for (VirtualFile file : classVFiles) {
          if (FileUtil.pathsEqual(file.getPath(), libRoot.getPath())) {
            return file;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findLibDir(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!file.isValid()) {
        continue;
      }
      if ((file.findChild("__future__.py") != null || file.findChild("__future__.pyc") != null) &&
          file.findChild("xml") != null && file.findChild("email") != null) {
        return file;
      }
    }
    return null;
  }
}
