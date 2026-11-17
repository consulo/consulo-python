// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.action.Presentation;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PythonConsoleStarter {
    private PythonConsoleStarter() {
    }

    /**
     * Schedules a console runner to be built, and hands it to {@code andThen} on the EDT once it is there.
     * <p>
     * The runner reaches {@code andThen} on the EDT because every method it offers there — {@code run}, {@code runSync},
     * {@code open} — touches the tool window.
     */
    public static void launchPythonConsoleRunner(Project project, @Nullable Module contextModule, Consumer<PydevConsoleRunner> andThen) {
        launch(project, contextModule, false, andThen);
    }

    /**
     * {@link #launchPythonConsoleRunner}, and nothing at all when the project has no interpreter to run on.
     * <p>
     * For the two places a console starts although nobody asked for one: opening the Python Console tool window, and
     * activating it while it holds no console. Neither is an action, so neither can grey itself out. Without the check
     * the runner starts, finds no interpreter, and throws {@code ExecutionException} into an error panel with a warning in
     * the log — see {@code PydevConsoleRunnerImpl.runSync} (PY-92174).
     */
    public static void launchPythonConsoleRunnerIfInterpreterExists(
        Project project,
        @Nullable Module contextModule,
        Consumer<PydevConsoleRunner> andThen
    ) {
        launch(project, contextModule, true, andThen);
    }

    private static void launch(
        Project project,
        @Nullable Module contextModule,
        boolean onlyWithInterpreter,
        Consumer<PydevConsoleRunner> andThen
    ) {
        CompletableFuture.supplyAsync(() -> ReadAction.compute(
                () -> !onlyWithInterpreter || PydevConsoleRunnerUtil.findPythonSdkAndModule(project, contextModule).first != null
            ))
            .thenCompose(hasInterpreter -> {
                if (!hasInterpreter) {
                    return CompletableFuture.completedFuture(null);
                }
                return PyConsoleRunnerFactoryAsync.getInstance().createConsoleRunnerAsync(project, contextModule);
            })
            .thenAccept(runner -> {
                if (runner != null) {
                    Application.get().invokeLater(() -> andThen.accept(runner), project::isDisposed);
                }
            });
    }

    /**
     * States on {@code presentation} whether a Python Console can start, and says why not when it cannot.
     * <p>
     * A console needs an interpreter, so an action that starts one greys out without it. A grey entry that says nothing
     * reads as a defect, and in a monorepo it is the state the project opens in: the startup autoconfiguration registers
     * no SDK for a project with more than one Python module (PY-92174). So {@code reason} goes on the description, which
     * the status bar shows.
     * <p>
     * {@code defaultDescription} is the action's own description, restored once an interpreter appears. A
     * {@code Presentation} is reused across updates, so a reason left over from an earlier one would otherwise outlive it.
     */
    public static void setConsoleInterpreterState(
        Presentation presentation,
        boolean hasInterpreter,
        LocalizeValue reason,
        LocalizeValue defaultDescription
    ) {
        presentation.setEnabled(hasInterpreter);
        presentation.setDescription(hasInterpreter ? defaultDescription : reason);
        // TODO consulo: ActionUtil.TOOLTIP_TEXT client property missing in consulo-ui-ex-api
    }

    /**
     * Whether a Python Console has an interpreter to start on.
     * <p>
     * For an action's {@code update}, which cannot wait.
     */
    public static boolean hasPythonConsoleInterpreter(Project project, @Nullable Module contextModule) {
        return ReadAction.compute(() -> PydevConsoleRunnerUtil.findPythonSdkAndModule(project, contextModule).first != null);
    }
}
