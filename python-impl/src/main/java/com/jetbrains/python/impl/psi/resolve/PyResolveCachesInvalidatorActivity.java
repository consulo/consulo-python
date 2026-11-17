// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;

/**
 * Starts {@link PyResolveCachesInvalidator}. Ends at once, so a test that waits for the startup activities does not hang.
 */
@ExtensionImpl
public final class PyResolveCachesInvalidatorActivity implements PostStartupActivity {
  @Override
  public void runActivity(Project project, UIAccess uiAccess) {
    PyResolveCachesInvalidator.getInstance(project).subscribe();
  }
}
