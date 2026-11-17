// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ExtensionImpl;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

@ExtensionImpl(id = "python")
public final class PythonConsoleCustomizer implements PyConsoleCustomizer {
    @Override
    public @Nullable Module guessConsoleModule(Project project, @Nullable Module contextModule) {
        return contextModule;
    }
}
