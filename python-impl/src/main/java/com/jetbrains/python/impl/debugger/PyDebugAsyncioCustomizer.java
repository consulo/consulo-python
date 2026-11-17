// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.impl.run.EnvironmentController;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyDebugAsyncioCustomizer {
    static PyDebugAsyncioCustomizer getInstance() {
        return Application.get().getExtensionList(PyDebugAsyncioCustomizer.class).get(0);
    }

    default void enableAsyncioMode(EnvironmentController environmentController) {
    }

    default boolean isAsyncioModeSupported() {
        return false;
    }
}
