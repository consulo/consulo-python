// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Service for checking package availability in Python environments.
 * <p>
 * Uses a combined strategy:
 * 1. First checks package manager cache (fast, non-blocking)
 * 2. Falls back to PSI resolution on disk (cached)
 * <p>
 * This service is implemented in a higher-level module (python.community.impl) that has access
 * to {@code com.jetbrains.python.packaging.management.PythonPackageManager}.
 */
@ServiceAPI(ComponentScope.APPLICATION)
public interface PackageAvailabilityService {
  /**
   * Checks if a package is available in the module's Python environment.
   */
  boolean isPackageAvailable(Module module, PackageAvailabilitySpec spec);

  /**
   * Checks if a package is available in the SDK's Python environment.
   */
  boolean isPackageAvailable(Project project, Sdk sdk, PackageAvailabilitySpec spec);

  /**
   * The version of the package [packageName] that the package manager lists as installed in [interpreter], or {@code null}
   * when it lists no such package. It reads the last list and does not wait, so code insight can call it.
   */
  @Nullable String installedPackageVersion(Project project, Sdk interpreter, String packageName);

  static PackageAvailabilityService getInstance() {
    return Application.get().getInstance(PackageAvailabilityService.class);
  }
}
