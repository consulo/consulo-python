// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.defaultProjectAwareService;

import consulo.module.Module;

public interface PyDefaultProjectAwareServiceModuleConfigurator {
  /**
   * Configures newly created module
   * @param newProject true if new project created
   */
  void configureModule(Module module, boolean newProject);
}
