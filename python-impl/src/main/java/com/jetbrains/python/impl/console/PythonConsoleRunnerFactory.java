// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;

/**
 * @deprecated extend {@link PyConsoleRunnerFactoryAsync} and implement its asynchronous methods.
 * <p>
 * This class only holds the extension point. It has no methods to build a runner.
 * {@link PyConsoleRunnerFactoryAsync#getInstance()} ignores a factory that extends only this class.
 */
@Deprecated
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PythonConsoleRunnerFactory {
    static ExtensionPoint<PythonConsoleRunnerFactory> getEpName() {
        return Application.get().getExtensionPoint(PythonConsoleRunnerFactory.class);
    }
}
