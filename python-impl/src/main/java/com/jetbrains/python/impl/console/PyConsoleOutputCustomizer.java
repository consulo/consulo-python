// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;

import java.util.Map;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyConsoleOutputCustomizer {
    static PyConsoleOutputCustomizer getInstance() {
        return Application.get().getExtensionPoint(PyConsoleOutputCustomizer.class).getExtensionList().getFirst();
    }

    default void showRichOutput(PythonConsoleView consoleView, Map<String, String> data) {
    }

    default boolean isInlineOutputSupported() {
        return false;
    }
}
