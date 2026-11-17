// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.dataholder.Key;

public final class PackageAvailabilityKt {
  private PackageAvailabilityKt() {
  }

  public static boolean isPackageAvailableKey(Key<?> key) {
    return key.toString().startsWith(PackageAvailabilitySpec.PREFIX);
  }

  /**
   * Checks if a package is available in the module's Python environment.
   * <p>
   * Uses a combined strategy:
   * 1. First checks package manager cache (fast)
   * 2. Falls back to PSI resolution if not found in package manager
   *
   * @param spec the package availability specification
   * @return true if the package is available, false otherwise
   */
  public static boolean isPackageAvailable(Module module, PackageAvailabilitySpec spec) {
    return PackageAvailabilityService.getInstance().isPackageAvailable(module, spec);
  }

  /**
   * Checks if a package is available in the SDK's Python environment.
   * <p>
   * Uses a combined strategy:
   * 1. First checks package manager cache (fast)
   * 2. Falls back to PSI resolution if not found in package manager
   *
   * @param spec the package availability specification
   * @return true if the package is available, false otherwise
   */
  public static boolean isPackageAvailable(Project project, Sdk sdk, PackageAvailabilitySpec spec) {
    return PackageAvailabilityService.getInstance().isPackageAvailable(project, sdk, spec);
  }
}
