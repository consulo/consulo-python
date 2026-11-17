// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.run.PythonRunConfiguration;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Builds the runner behind a Python Console.
 * <p>
 * Both methods are asynchronous, because building a runner waits for the project model. A synchronous read answers
 * {@code null} for a module that has an interpreter while the SDK table still loads, and the console then starts on the
 * wrong interpreter or on none. See {@code findPythonSdkAndModule}.
 */
@SuppressWarnings("deprecation")
public abstract class PyConsoleRunnerFactoryAsync extends PythonConsoleRunnerFactory {

    public abstract CompletableFuture<PydevConsoleRunner> createConsoleRunnerAsync(Project project, @Nullable Module contextModule);

    public abstract CompletableFuture<PydevConsoleRunner> createConsoleRunnerWithFileAsync(Project project, PythonRunConfiguration config);

    /**
     * The registered factory.
     * <p>
     * A factory that still extends only the deprecated {@link PythonConsoleRunnerFactory} is not one of these, and this
     * skips it. Such a factory has no methods to build a runner.
     */
    public static PyConsoleRunnerFactoryAsync getInstance() {
        return getEpName().getExtensionList().stream()
            .filter(PyConsoleRunnerFactoryAsync.class::isInstance)
            .map(PyConsoleRunnerFactoryAsync.class::cast)
            .findFirst()
            .orElseThrow();
    }
}
