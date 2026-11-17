// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.sdk.ModuleOrProject;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScopesCore;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.Set;

public final class PySearchScopeBuilder {
  private static final Set<String> STDLIB_TEST_DIRS = Set.of(
    "bsddb/test",
    "ctypes/test",
    "distutils/tests",
    "email/test",
    "idlelib/idle_test",
    "importlib/test",
    "json/tests",
    "lib-tk/test",
    "lib2to3/tests",
    "sqlite3/test",
    "test",
    "tkinter/test",
    "unittest/test"
  );
  private static final Set<String> TEST_ROOT_NAMES = Set.of("test", "tests");
  private static final Set<String> BUNDLED_DEPS_ROOT_NAMES = Set.of("_vendor", "vendor", "vendored", "_vendored_packages");
  private static final Set<String> THIRD_PARTY_PACKAGE_ROOT_NAMES = Set.of(PyNames.SITE_PACKAGES, PyNames.DIST_PACKAGES);

  private boolean myExcludeStdlibTests = false;
  private boolean myExcludeThirdPartyBundledDeps = false;
  private boolean myExcludeThirdPartyTests = false;
  private final ModuleOrProject myModuleOrProject;
  private final List<Sdk> myEffectiveSdks;

  /**
   * Creates a new builder for the given Python SDK.
   */
  public static PySearchScopeBuilder forProject(Project project) {
    return new PySearchScopeBuilder(new ModuleOrProject.ProjectOnly(project));
  }

  /**
   * Creates a new builder, deducing Python SDK from the given element.
   * <p>
   * The element's own module answers first, then {@link MainPythonSdkKt#mainPythonSdk}.
   */
  public static PySearchScopeBuilder forModuleOf(PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return new PySearchScopeBuilder(
      module != null ?
      new ModuleOrProject.ModuleAndProject(module) :
      new ModuleOrProject.ProjectOnly(element.getProject())
    );
  }

  private PySearchScopeBuilder(ModuleOrProject moduleOrProject) {
    myModuleOrProject = moduleOrProject;
    myEffectiveSdks = findEffectivePythonSdks(moduleOrProject);
  }

  /**
   * Excludes tests of the standard library from the resulting scope, e.g. "ctypes/test" or "tkinter/test".
   */
  public PySearchScopeBuilder excludeStandardLibraryTests() {
    myExcludeStdlibTests = true;
    return this;
  }

  /**
   * Excludes "vendored" dependencies bundled with third-party packages from the resulting scope, e.g. the content of "pip/_vendor".
   */
  public PySearchScopeBuilder excludeThirdPartyPackageBundledDependencies() {
    myExcludeThirdPartyBundledDeps = true;
    return this;
  }

  /**
   * Excludes tests bundled with third-party packages from the resulting scope, e.g. "numpy/tests" or "scipy/stats/tests".
   */
  public PySearchScopeBuilder excludeThirdPartyPackageTests() {
    myExcludeThirdPartyTests = true;
    return this;
  }

  /**
   * Builds a {@link GlobalSearchScope} instance for the specified SDK according to the configuration.
   */
  public GlobalSearchScope build() {
    Project project = myModuleOrProject.getProject();
    GlobalSearchScope scope;
    if (myModuleOrProject instanceof ModuleOrProject.ModuleAndProject moduleAndProject) {
      scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleAndProject.getModule());
    }
    else {
      scope = GlobalSearchScope.allScope(project);
    }
    if (myExcludeStdlibTests) {
      scope = scope.intersectWith(GlobalSearchScope.notScope(buildStdlibTestsScope()));
    }
    if (myExcludeThirdPartyTests || myExcludeThirdPartyBundledDeps) {
      scope = scope.intersectWith(GlobalSearchScope.notScope(new QualifiedNameFinder.QualifiedNameBasedScope(project) {
        @Override
        protected boolean containsQualifiedNameInRoot(VirtualFile root, QualifiedName qName) {
          if (THIRD_PARTY_PACKAGE_ROOT_NAMES.contains(root.getName())) {
            List<String> internalQNameComponents = qName.removeHead(1).getComponents();
            if (myExcludeThirdPartyTests && ContainerUtil.exists(internalQNameComponents, TEST_ROOT_NAMES::contains)) {
              return true;
            }
            if (myExcludeThirdPartyBundledDeps && ContainerUtil.exists(internalQNameComponents, BUNDLED_DEPS_ROOT_NAMES::contains)) {
              return true;
            }
          }
          return false;
        }
      }));
    }
    return scope;
  }

  private GlobalSearchScope buildStdlibTestsScope() {
    return StreamEx.of(myEffectiveSdks)
      .map(sdk -> PySearchUtilBase.findLibDir(sdk))
      .nonNull()
      .flatMap(libDir -> StreamEx.of(STDLIB_TEST_DIRS)
        .map(relPath -> libDir.findFileByRelativePath(relPath))
        .nonNull()
        .map(dir -> GlobalSearchScopesCore.directoryScope(myModuleOrProject.getProject(), dir, true))
      )
      .reduce(GlobalSearchScope.EMPTY_SCOPE, GlobalSearchScope::union);
  }

  private static List<Sdk> findEffectivePythonSdks(ModuleOrProject moduleOrProject) {
    if (moduleOrProject instanceof ModuleOrProject.ModuleAndProject moduleAndProject) {
      Sdk sdk = MainPythonSdkKt.pythonSdkIfReady(moduleAndProject.getModule());
      if (sdk != null) {
        return List.of(sdk);
      }
    }
    else {
      // If there is no "active" Module for the scope, exclude stdlib tests for all configured project SDKs
      ModuleManager moduleManager = ModuleManager.getInstance(moduleOrProject.getProject());
      if (moduleManager != null) {
        return StreamEx.of(moduleManager.getModules())
          .map(m -> MainPythonSdkKt.pythonSdkIfReady(m))
          .nonNull()
          .distinct()
          .toList();
      }
    }
    Sdk mainPythonSdk = MainPythonSdkKt.mainPythonSdk(moduleOrProject.getProject());
    return mainPythonSdk != null ? List.of(mainPythonSdk) : List.of();
  }
}
