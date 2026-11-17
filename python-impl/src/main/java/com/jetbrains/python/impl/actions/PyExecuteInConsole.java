// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.impl.console.DescriptorType;
import com.jetbrains.python.impl.console.PyCodeExecutor;
import com.jetbrains.python.impl.console.PyConsoleRunnerFactoryAsync;
import com.jetbrains.python.impl.console.PyExecuteConsoleCustomizer;
import com.jetbrains.python.impl.console.PydevConsoleRunner;
import com.jetbrains.python.impl.console.PythonConsoleToolWindow;
import com.jetbrains.python.impl.console.PythonConsoleView;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.impl.run.PythonRunConfiguration;
import consulo.application.Application;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Top-level functions of JetBrains' PyExecuteInConsole.kt ({@code @file:JvmName("PyExecuteInConsole")}).
 * <p>
 * The {@code TargetEnvironmentFunction} overload of {@code executeCodeInConsole} is not ported: it belongs to the Target API.
 */
public final class PyExecuteInConsole {
    private PyExecuteInConsole() {
    }

    public static void executeCodeInConsole(
        Project project,
        @Nullable String commandText,
        @Nullable Editor editor,
        boolean canUseExistingConsole,
        boolean canUseDebugConsole,
        boolean requestFocusToConsole,
        @Nullable PythonRunConfiguration config
    ) {
        Consumer<ExecutionConsole> executeCodeInConsole =
            commandText == null ? null : console -> ((PyCodeExecutor) console).executeCode(commandText, editor);
        Function<VirtualFile, Boolean> executeInStartingConsole =
            file -> PyExecuteConsoleCustomizer.getInstance().isConsoleStarting(file, commandText);
        executeCodeInConsole(
            project,
            executeCodeInConsole,
            executeInStartingConsole,
            editor,
            canUseExistingConsole,
            canUseDebugConsole,
            requestFocusToConsole,
            config
        );
    }

    private static void executeCodeInConsole(
        Project project,
        @Nullable Consumer<ExecutionConsole> executeInConsole,
        Function<VirtualFile, Boolean> executeInStartingConsole,
        @Nullable Editor editor,
        boolean canUseExistingConsole,
        boolean canUseDebugConsole,
        boolean requestFocusToConsole,
        @Nullable PythonRunConfiguration config
    ) {
        RunContentDescriptor existingConsole = null;
        boolean isDebug = false;
        PydevConsoleRunner.ConsoleListener newConsoleListener = null;
        VirtualFile virtualFile = editor != null ? editor.getVirtualFile() : null;
        if (canUseExistingConsole) {
            if (virtualFile != null && PyExecuteConsoleCustomizer.getInstance().isCustomDescriptorSupported(virtualFile)) {
                Pair<RunContentDescriptor, PydevConsoleRunner.ConsoleListener> pair = getCustomDescriptor(project, virtualFile);
                existingConsole = pair.getFirst();
                newConsoleListener = pair.getSecond();
            }
            else {
                existingConsole = getSelectedPythonConsole(project);
            }
            if (canUseDebugConsole) {
                PythonConsoleToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(project);
                if (consoleToolWindow == null || !consoleToolWindow.getToolWindow().isVisible()) {
                    // PY-48207 Currently visible Python Console has a higher priority than a Debug console
                    RunContentDescriptor debugConsole = getCurrentDebugConsole(project);
                    if (debugConsole != null) {
                        existingConsole = debugConsole;
                        isDebug = true;
                    }
                }
            }
        }
        if (existingConsole != null) {
            ExecutionConsole console = existingConsole.getExecutionConsole();
            if (executeInConsole != null) {
                executeInConsole.accept(console);
            }
            PythonConsoleView consoleView = showConsole(project, existingConsole, isDebug);
            requestFocus(requestFocusToConsole, editor, consoleView, isDebug);
        }
        else {
            if (!executeInStartingConsole.apply(virtualFile)) {
                startNewConsoleInstance(project, virtualFile, executeInConsole, config, newConsoleListener, requestFocusToConsole);
            }
        }
    }

    public static Pair<RunContentDescriptor, PydevConsoleRunner.ConsoleListener> getCustomDescriptor(
        Project project,
        VirtualFile virtualFile
    ) {
        PyExecuteConsoleCustomizer executeCustomizer = PyExecuteConsoleCustomizer.getInstance();
        DescriptorType type = executeCustomizer.getCustomDescriptorType(virtualFile);
        if (type == null) {
            throw new IllegalStateException("Custom descriptor for " + virtualFile + " is null");
        }
        switch (type) {
            case NEW:
                return Pair.create(null, createNewConsoleListener(project, virtualFile));
            case EXISTING: {
                RunContentDescriptor console = executeCustomizer.getExistingDescriptor(virtualFile);
                if (console != null && isAlive(console)) {
                    return Pair.create(console, null);
                }
                else {
                    return Pair.create(null, createNewConsoleListener(project, virtualFile));
                }
            }
            case STARTING:
                return Pair.create(null, null);
            default:
                throw new IllegalStateException("Custom descriptor for " + virtualFile + " is null");
        }
    }

    private static PydevConsoleRunner.ConsoleListener createNewConsoleListener(Project project, VirtualFile virtualFile) {
        return consoleView -> {
            List<RunContentDescriptor> consoles = getAllRunningConsoles(project);
            RunContentDescriptor newDescriptor = consoles.stream()
                .filter(it -> it.getExecutionConsole() == consoleView)
                .findFirst()
                .orElse(null);
            PyExecuteConsoleCustomizer.getInstance().updateDescriptor(virtualFile, DescriptorType.EXISTING, newDescriptor);
        };
    }

    private static @Nullable RunContentDescriptor getCurrentDebugConsole(Project project) {
        XDebugSession currentSession = XDebuggerManager.getInstance(project).getCurrentSession();
        if (currentSession != null) {
            RunContentDescriptor descriptor = currentSession.getRunContentDescriptor();
            if (isAlive(descriptor)) {
                return descriptor;
            }
        }
        return null;
    }

    private static List<RunContentDescriptor> getAllRunningConsoles(Project project) {
        PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);
        if (toolWindow != null && toolWindow.isInitialized()) {
            return toolWindow.getConsoleContentDescriptors().stream().filter(PyExecuteInConsole::isAlive).toList();
        }
        else {
            return List.of();
        }
    }

    public static @Nullable RunContentDescriptor getSelectedPythonConsole(Project project) {
        PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);
        if (toolWindow == null) {
            return null;
        }
        if (!toolWindow.isInitialized()) {
            return null;
        }
        List<RunContentDescriptor> consoles =
            toolWindow.getConsoleContentDescriptors().stream().filter(PyExecuteInConsole::isAlive).toList();
        if (consoles.size() == 1) {
            return consoles.get(0);
        }
        RunContentDescriptor selected = toolWindow.getSelectedContentDescriptor();
        if (selected != null && consoles.contains(selected)) {
            return selected;
        }
        return consoles.isEmpty() ? null : consoles.get(0);
    }

    private static boolean isAlive(RunContentDescriptor dom) {
        ProcessHandler processHandler = dom.getProcessHandler();
        return processHandler != null && !processHandler.isProcessTerminated();
    }

    private static void startNewConsoleInstance(
        Project project,
        @Nullable VirtualFile virtualFile,
        @Nullable Consumer<ExecutionConsole> executeInConsole,
        @Nullable PythonRunConfiguration config,
        PydevConsoleRunner.@Nullable ConsoleListener listener,
        boolean isRequestFocus
    ) {
        PyConsoleRunnerFactoryAsync consoleRunnerFactory = PyConsoleRunnerFactoryAsync.getInstance();
        CompletableFuture<PydevConsoleRunner> runnerFuture;
        if (executeInConsole == null || config == null) {
            runnerFuture = consoleRunnerFactory.createConsoleRunnerAsync(project, null);
        }
        else {
            runnerFuture = consoleRunnerFactory.createConsoleRunnerWithFileAsync(project, config);
        }

        runnerFuture.thenAccept(runner -> Application.get().invokeLater(() -> {
            runner.addConsoleListener(consoleView -> {
                if (consoleView instanceof PyCodeExecutor) {
                    if (executeInConsole != null) {
                        executeInConsole.accept(consoleView);
                    }
                    PythonConsoleToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(project);
                    if (consoleToolWindow != null) {
                        consoleToolWindow.getToolWindow().show(null);
                    }
                }
            });
            if (listener != null) {
                runner.addConsoleListener(listener);
            }
            if (virtualFile != null) {
                PyExecuteConsoleCustomizer.getInstance().notifyRunnerStart(virtualFile, runner);
            }
            runner.run(isRequestFocus);
        }, project.getDisposed()));
    }

    private static @Nullable PythonConsoleView showConsole(Project project, RunContentDescriptor descriptor, boolean isDebug) {
        if (isDebug) {
            ExecutionConsole console = descriptor.getExecutionConsole();
            XDebugSession currentSession = XDebuggerManager.getInstance(project).getCurrentSession();
            if (currentSession != null) {
                ToolWindow debugToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG);
                if (debugToolWindow != null) {
                    if (!debugToolWindow.isVisible()) {
                        debugToolWindow.show(null);
                    }
                }
                // Select "Console" tab in case of Debug console
                RunnerLayoutUi sessionUi = currentSession.getUI();
                if (sessionUi != null) {
                    selectConsoleTab(descriptor, sessionUi.getContentManager(), true);
                }
                else {
                    // TODO [Debugger.RunnerLayoutUi]
                }
                return ((PythonDebugLanguageConsoleView) console).getPydevConsoleView();
            }
        }
        else {
            PythonConsoleToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(project);
            if (consoleToolWindow != null) {
                ToolWindow toolWindow = consoleToolWindow.getToolWindow();
                if (!toolWindow.isVisible()) {
                    Application.get().invokeLater(() -> toolWindow.show(null));
                }
                selectConsoleTab(descriptor, toolWindow.getContentManager(), false);
            }
            return descriptor.getExecutionConsole() instanceof PythonConsoleView pythonConsoleView ? pythonConsoleView : null;
        }
        return null;
    }

    private static void selectConsoleTab(RunContentDescriptor descriptor, ContentManager contentManager, boolean isDebug) {
        String tabName = isDebug ? "Console" : PyExecuteConsoleCustomizer.getInstance().getDescriptorName(descriptor);
        Content content = contentManager.findContent(tabName);
        if (content != null) {
            contentManager.setSelectedContent(content);
        }
    }

    public static void requestFocus(boolean requestFocusToConsole,
                                    @Nullable Editor editor,
                                    @Nullable PythonConsoleView consoleView,
                                    boolean isDebug) {
        if (requestFocusToConsole) {
            if (consoleView != null) {
                if (isDebug) {
                    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
                        IdeFocusManager.getGlobalInstance().requestFocus(consoleView.getConsoleEditor().getContentComponent(), true);
                    });
                }
                else {
                    consoleView.requestFocus();
                }
            }
        }
        else {
            if (editor != null) {
                IdeFocusManager.findInstance().requestFocus(editor.getContentComponent(), true);
            }
        }
    }
}
