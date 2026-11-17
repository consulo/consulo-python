// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyConsoleCustomizer {
    @Nullable
    Module guessConsoleModule(Project project, @Nullable Module contextModule);
}
