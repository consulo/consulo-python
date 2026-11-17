// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.debugger.PyDebugProcess;
import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import consulo.execution.debug.XDebugSession;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.platform.ForwardedPort;
import consulo.platform.Platform;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.Pair;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

class PyConsoleDebugProcess extends PyDebugProcess {
    private final int myLocalPort;
    private final PyConsoleDebugProcessHandler myConsoleDebugProcessHandler;
    /**
     * Consulo-specific: the platform of the console process, see {@link #connect(PydevConsoleCommunication)}.
     */
    private final Platform myConsolePlatform;

    PyConsoleDebugProcess(
        XDebugSession session,
        ServerSocket serverSocket,
        ExecutionConsole executionConsole,
        PyConsoleDebugProcessHandler consoleDebugProcessHandler,
        Platform consolePlatform
    ) {
        super(session, serverSocket, executionConsole, consoleDebugProcessHandler, false);
        myLocalPort = serverSocket.getLocalPort();
        myConsoleDebugProcessHandler = consoleDebugProcessHandler;
        myConsolePlatform = consolePlatform;
    }

    @Override
    public void sessionInitialized() {
        //nop
    }

    @Override
    protected String getConnectionMessage() {
        return PyLocalize.progressTextConnectingToConsole().get();
    }

    @Override
    protected String getConnectionTitle() {
        return PyLocalize.progressTitleDebuggerConnection().get();
    }

    @Override
    protected void detachDebuggedProcess() {
        //TODO: implement disconnect
    }

    @Override
    protected void beforeConnect() {
        printToConsole(getCurrentStateMessage().get() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }


    @Override
    public int getConnectTimeout() {
        return 0; //server should not stop
    }

    public void connect(PydevConsoleCommunication consoleCommunication) throws Exception {
        Pair<String, Integer> portToConnect;
        if (!myConsolePlatform.equals(Platform.current())) {
            // Consulo-specific: JB maps the port through RemoteProcessControl. The console process of another machine
            // connects to the debugger port of the IDE published on that machine, which lives as long as the console.
            ForwardedPort publishedPort = myConsolePlatform.net().publish(myLocalPort);
            myConsoleDebugProcessHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(ProcessEvent event) {
                    publishedPort.close();
                }
            });
            portToConnect = Pair.create(publishedPort.host(), publishedPort.port());
        }
        else {
            portToConnect = Pair.create("localhost", myLocalPort);
        }
        Map<String, Boolean> optionsMap = makeDebugOptionsMap(getSession());
        Map<String, String> envs = getDebuggerEnvs(getSession());
        consoleCommunication.connectToDebugger(portToConnect.getSecond(), portToConnect.getFirst(), optionsMap, envs);
    }

    public static Map<String, String> getDebuggerEnvs(XDebugSession session) {
        Map<String, String> env = new HashMap<>();
        PyDebugRunner.configureDebugEnvironment(session.getProject(), env, session.getRunProfile());
        return env;
    }

    public static Map<String, Boolean> makeDebugOptionsMap(XDebugSession session) {
        Project project = session.getProject();
        PyDebuggerOptionsProvider userOpts = PyDebuggerOptionsProvider.getInstance(project);
        Map<String, Boolean> dbgOpts = new HashMap<>();
        dbgOpts.put("save-signatures", userOpts.isSaveCallSignatures());
        dbgOpts.put("qt-support", userOpts.isSupportQtDebugging());
        return dbgOpts;
    }

    public void waitForNextConnection() {
        if (isConnected()) {
            disconnect();
        }
        if (getSession().isSuspended()) {
            getSession().resume();
        }
        if (!isWaitingForConnection()) {
            setWaitingForConnection(true);

            UIUtil.invokeLaterIfNeeded(() -> waitForConnection(getCurrentStateMessage().get(), getConnectionTitle()));
        }
    }
}
