// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.util.ModificationTracker;
import consulo.component.util.SimpleModificationTracker;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Drops the resolve caches when a snapshot gives a module another interpreter.
 * <p>
 * Resolve reads the interpreter from the snapshot, see {@link MainPythonSdkKt#pythonSdkIfReady(Module)}, and caches what it finds against PSI
 * changes. A snapshot lands after the roots change that set the interpreter, and before the first snapshot resolve
 * sees no interpreter at all. A daemon restart reruns the inspections on these cached results, so it is not enough.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PyResolveCachesInvalidator {
  private final Project project;
  private final SimpleModificationTracker tracker = new SimpleModificationTracker();

  @Inject
  public PyResolveCachesInvalidator(Project project) {
    this.project = project;
  }

  /**
   * Changes each time a snapshot changes the interpreter of a module. For a cache that reads the snapshot.
   */
  public ModificationTracker getModificationTracker() {
    return tracker;
  }

  void subscribe() {
    // TODO consulo: EvoPyProjectModel.snapshotFlow / PythonInterpreterRoots.changes missing
  }

  private void dropCaches(Map<Module, Sdk> interpreters) {
    for (Map.Entry<Module, Sdk> entry : interpreters.entrySet()) {
      Module module = entry.getKey();
      if (module.isDisposed()) continue;
      PythonModulePathCache.getInstance(module).clearCache();
      Sdk interpreter = entry.getValue();
      if (interpreter != null) {
        PythonSdkPathCache.getInstance(project, interpreter).clearCache();
      }
    }
    tracker.incModificationCount();
    project.getApplication().runWriteAction(() -> PsiManager.getInstance(project).dropPsiCaches());
  }

  public static PyResolveCachesInvalidator getInstance(Project project) {
    return project.getInstance(PyResolveCachesInvalidator.class);
  }
}
