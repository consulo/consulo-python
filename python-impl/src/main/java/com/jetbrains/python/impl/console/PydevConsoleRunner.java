// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import consulo.content.bundle.Sdk;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.process.ProcessHandler;
import consulo.ui.ex.action.AnAction;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

public interface PydevConsoleRunner {
    Key<ConsoleCommunication> CONSOLE_COMMUNICATION_KEY = new Key<>("PYDEV_CONSOLE_COMMUNICATION_KEY");
    Key<Sdk> CONSOLE_SDK = new Key<>("PYDEV_CONSOLE_SDK_KEY");

    interface ConsoleListener {
        void handleConsoleInitialized(LanguageConsoleView consoleView);
    }

    void open();

    void runSync(boolean requestEditorFocus);

    void run(boolean requestEditorFocus);

    void reRun(boolean requestEditorFocus, @Nullable String title);

    PydevConsoleCommunication getPydevConsoleCommunication();

    void addConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

    void removeConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

    PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler();

    ProcessHandler getProcessHandler();

    PythonConsoleView getConsoleView();

    default @Nullable AnAction createRerunAction() {
        return null;
    }

    void setSdk(@Nullable Sdk sdk);

    @Nullable
    Sdk getSdk();
}
