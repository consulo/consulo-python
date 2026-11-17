// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.sdk.PythonInterpreterExtKt;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class PySearchUtilBase {

  /**
   * Creates a scope most suitable for suggesting symbols and files to a user, i.e. in auto-importing or "extended" completion.
   * <p>
   * This scope covers the project's own sources and its libraries, but excludes
   * <ul>
   *   <li>Standard library tests</li>
   *   <li>Stubs for third-party packages in Typeshed</li>
   *   <li>Bundled tests of third-party packages</li>
   *   <li>Bundled dependencies of third-party packages</li>
   * </ul>
   *
   * @param anchor element to detect the corresponding Python SDK
   * @see PySearchScopeBuilder
   */
  public static GlobalSearchScope defaultSuggestionScope(PsiElement anchor) {
    return PySearchScopeBuilder.forModuleOf(anchor)
      .excludeStandardLibraryTests()
      .excludeThirdPartyPackageTests()
      .excludeThirdPartyPackageBundledDependencies()
      .build();
  }

  /**
   * Calculates a search scope which excludes Python standard library tests. Using such scope may be quite a bit slower than using
   * the regular "project and libraries" search scope, so it should be used only for displaying the list of variants to the user
   * (for example, for class name completion or auto-import).
   *
   * @param project the project for which the scope should be calculated
   * @return the resulting scope
   */
  public static GlobalSearchScope excludeSdkTestsScope(Project project) {
    return excludeSdkTestScope(GlobalSearchScope.allScope(project));
  }

  public static GlobalSearchScope excludeSdkTestScope(GlobalSearchScope scope) {
    Project project = Objects.requireNonNull(scope.getProject());
    // TODO cache the scope in project userdata (update when SDK paths change or different project SDK is selected)
    return scope.intersectWith(PySearchScopeBuilder.forProject(project)
                                 .excludeStandardLibraryTests()
                                 .build());
  }

  public static @Nullable VirtualFile findLibDir(Sdk sdk) {
    return PythonInterpreterExtKt.stdlibLibDirectory(sdk);
  }
}
