// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.PydevConsoleExecuteActionHandler;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.process.ProcessHandler;
import consulo.python.impl.localize.PyLocalize;

public class PydevDebugConsoleExecuteActionHandler extends PydevConsoleExecuteActionHandler implements XDebugSessionListener {
    private final PythonDebugLanguageConsoleView myConsole;

    public PydevDebugConsoleExecuteActionHandler(PythonDebugLanguageConsoleView console,
                                                 ProcessHandler processHandler,
                                                 ConsoleCommunication consoleCommunication) {
        super(console.getPydevConsoleView(), processHandler, consoleCommunication);
        myConsole = console;
    }

    @Override
    protected boolean isCommandQueueEnabled() {
        return PyDebuggerOptionsProvider.getInstance(myConsole.getPydevConsoleView().getProject()).isDebugConsoleCommandQueueEnabled();
    }

    @Override
    protected String getConsoleIsNotEnabledMessage() {
        return PyLocalize.debuggerPydevConsolePauseTheProcessToUseCommandLine().get();
    }

    @Override
    public void sessionPaused() {
        setEnabled(true);
        // The start script needs a stack frame, so the first pause is the earliest moment it can run.
        myConsole.executeStartScriptIfNeeded();
    }

    @Override
    public void sessionResumed() {
        if (!getConsoleCommunication().isWaitingForInput()) {
            setEnabled(false);
        }
    }

    @Override
    public void sessionStopped() {
        setEnabled(false);
    }

    @Override
    public void stackFrameChanged() {
    }

    @Override
    public void beforeSessionResume() {
    }

    @Override
    protected void beforeExecution(LanguageConsoleView consoleView) {
        super.beforeExecution(consoleView);
        String text = consoleView.getCurrentEditor().getDocument().getText();
        myConsole.getPrimaryConsoleView().print(text + '\n', ConsoleViewContentType.NORMAL_OUTPUT);
    }
}
