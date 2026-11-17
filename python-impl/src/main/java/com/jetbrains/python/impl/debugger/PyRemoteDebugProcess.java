// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.process.ProcessHandler;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.ExecutionConsole;
import consulo.application.Application;
import consulo.util.lang.StringUtil;
import consulo.execution.debug.XDebugSession;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.net.ServerSocket;

public class PyRemoteDebugProcess extends PyDebugProcess {
    private final int myLocalPort;
    private final String mySettraceCall;
    private boolean isStopCalled = false;

    public PyRemoteDebugProcess(XDebugSession session,
                                ServerSocket serverSocket,
                                ExecutionConsole executionConsole,
                                @Nullable ProcessHandler processHandler, @Nullable String settraceCall) {
        super(session, serverSocket, executionConsole, processHandler, false);
        if (processHandler instanceof PyRemoteDebugProcessAware) {
            ((PyRemoteDebugProcessAware)processHandler).setRemoteDebugProcess(this);
        }
        myLocalPort = serverSocket.getLocalPort();
        mySettraceCall = settraceCall;
    }

    @Override
    public void sessionInitialized() {
        super.sessionInitialized();
        printConsoleInfo();
    }

    protected void printConsoleInfo() {
        printToConsole(PyLocalize.debuggerRemoteStartingDebugServerAtPort(myLocalPort).get(), ConsoleViewContentType.SYSTEM_OUTPUT);
        printToConsole(PyLocalize.debuggerUseTheFollowingCodeToConnectToTheDebugger().get(), ConsoleViewContentType.SYSTEM_OUTPUT);
        if (!StringUtil.isEmpty(mySettraceCall)) {
            printToConsole(mySettraceCall + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        }
    }

    @Override
    protected String getConnectionMessage() {
        return PyLocalize.debuggerRemoteWaitingForProcessConnection().get();
    }

    @Override
    protected String getConnectionTitle() {
        return PyLocalize.debuggerRemoteWaitingForConnection().get();
    }

    @Override
    protected boolean shouldLogConnectionException(Exception e) {
        return !(isStopCalled && e.getMessage().contains("closed"));
    }

    @Override
    protected void detachDebuggedProcess() {
        waitForNextConnection(); // in case of remote debug we should wait for the next connection
    }

    @Override
    public void stop() {
        super.stop();
        isStopCalled = true;
    }

    @Override
    protected void beforeConnect() {
        printToConsole(getCurrentStateMessage().get() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public void waitForNextConnection() {
        if (isConnected()) {
            disconnect();
        }
        if (getSession().isSuspended()) {
            getSession().resume();
        }
        if (!isWaitingForConnection()) {
            if (!isStopCalled) {
                setWaitingForConnection(true);
                Application.get().invokeLater(() -> waitForConnection(getCurrentStateMessage().get(), getConnectionTitle()),
                                                                Application.get().getDefaultModalityState());
            }
        }
    }

    @Override
    public int getConnectTimeout() {
        return 0; //server should not stop
    }
}
