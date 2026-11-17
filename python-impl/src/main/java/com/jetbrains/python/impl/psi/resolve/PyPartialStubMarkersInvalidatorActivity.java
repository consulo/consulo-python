// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;

@ExtensionImpl
public final class PyPartialStubMarkersInvalidatorActivity implements PostStartupActivity {
    @Override
    public void runActivity(Project project, UIAccess uiAccess) {
        PyPartialStubMarkersInvalidator.getInstance(project).subscribe();
    }
}
