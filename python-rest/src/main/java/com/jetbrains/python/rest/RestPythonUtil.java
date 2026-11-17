// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import com.jetbrains.python.impl.psi.resolve.PackageAvailabilityKt;
import com.jetbrains.python.impl.psi.resolve.PackageAvailabilitySpec;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

/**
 * User : catherine
 */
public final class RestPythonUtil {
    private static final PackageAvailabilitySpec SPHINX_MARKER = new PackageAvailabilitySpec("Sphinx", "sphinx.application.Sphinx");

    private RestPythonUtil() {
    }

    public static Presentation updateSphinxQuickStartRequiredAction(AnActionEvent e) {
        Presentation presentation = e.getPresentation();

        Module module = e.getData(Module.KEY);
        if (module == null) {
            Project project = e.getData(Project.KEY);
            if (project != null) {
                Module[] modules = ModuleManager.getInstance(project).getModules();
                module = modules.length == 0 ? null : modules[0];
            }
        }
        if (module == null) {
            return presentation;
        }

        presentation.setEnabled(PackageAvailabilityKt.isPackageAvailable(module, SPHINX_MARKER));
        return presentation;
    }
}
