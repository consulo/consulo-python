// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import consulo.disposer.Disposer;
import consulo.process.KillableProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.dataholder.Key;

public final class PyConsoleProcessHandlers {
    public static final Key<PydevConsoleCommunication> pydevConsoleCommunicationKey = Key.create("PydevConsoleCommunication");

    private PyConsoleProcessHandlers() {
    }

    static void configureProcessHandlerForPythonConsole(
        ProcessHandler processHandler,
        PythonConsoleView consoleView,
        PydevConsoleCommunication consoleCommunication
    ) {
        Disposer.register(consoleView, () -> {
            if (!processHandler.isProcessTerminated()) {
                processHandler.destroyProcess();
            }
        });
        processHandler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(ProcessEvent event) {
                doCloseCommunication(consoleCommunication);
            }
        });

        if (processHandler instanceof KillableProcessHandler) {
            // TODO consulo: KillableProcessHandler.setShouldKillProcessSoftly(false) missing
        }

        processHandler.putUserData(pydevConsoleCommunicationKey, consoleCommunication);
    }

    private static void doCloseCommunication(PydevConsoleCommunication consoleCommunication) {
        UIUtil.invokeAndWaitIfNeeded((Runnable) () -> {
            try {
                consoleCommunication.close();
                Thread.sleep(300);
            }
            catch (Exception e) {
                // Ignore
            }
        });

        // waiting for REPL communication before destroying process handler
    }
}
