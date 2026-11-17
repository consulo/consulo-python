// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.application.util.CachedValue;
import consulo.util.dataholder.Key;

import java.util.List;

/**
 * Specification for checking package availability in a Python environment.
 * <p>
 * Uses a combined strategy:
 * 1. First checks the package manager cache (fast, non-blocking)
 * 2. Falls back to PSI resolution on disk (cached)
 * <p>
 * Create instances as constants and pass them to {@link PackageAvailabilityKt#isPackageAvailable}:
 * <pre>{@code
 * private val PYTEST_SPEC = PackageAvailabilitySpec("pytest", "pytest.fixture")
 *
 * fun isPytestInstalled(module: Module) = module.isPackageAvailable(PYTEST_SPEC)
 * }</pre>
 * <p>
 * Multiple FQNs can be provided for packages that expose symbols at different paths across versions:
 * <pre>{@code
 * private val BLACK_SPEC = PackageAvailabilitySpec("black", "black.Mode", "black.mode.Mode")
 * }</pre>
 *
 * @param packageName the package name as it appears in pip/package manager (e.g., "pytest", "Django", "djangorestframework")
 * @param fqns        the fully-qualified names to resolve for PSI fallback (e.g., "pytest.fixture", "django.conf.settings").
 *                    Resolution succeeds if any of the FQNs resolves.
 */
public final class PackageAvailabilitySpec {
  static final String PREFIX = "isPackageAvailable:";

  private final String packageName;
  private final List<String> fqns;
  private final Key<CachedValue<Boolean>> cacheKey;

  public PackageAvailabilitySpec(String packageName, String... fqns) {
    this.packageName = packageName;
    this.fqns = List.of(fqns);
    this.cacheKey = Key.create(PREFIX + packageName + ":" + String.join(",", fqns));
  }

  public String getPackageName() {
    return packageName;
  }

  public List<String> getFqns() {
    return fqns;
  }

  public Key<CachedValue<Boolean>> getCacheKey() {
    return cacheKey;
  }
}
