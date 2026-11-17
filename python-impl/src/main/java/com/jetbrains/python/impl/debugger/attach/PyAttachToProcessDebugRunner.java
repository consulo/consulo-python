// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.attach;

import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.debugger.PyLocalPositionConverter;
import com.jetbrains.python.impl.debugger.PyRemoteDebugProcess;
import com.jetbrains.python.impl.run.CommandLinePatcher;
import consulo.application.WriteAction;
import consulo.content.bundle.Sdk;
import consulo.document.FileDocumentManager;
import consulo.execution.ExecutionResult;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugProcessStarter;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;

public class PyAttachToProcessDebugRunner extends PyDebugRunner {
    private final Project myProject;
    private final int myPid;
    private final @Nullable String mySdkPath;
    private final @Nullable Sdk mySdk;


    public PyAttachToProcessDebugRunner(Project project, int pid, @Nullable Sdk sdk) {
        myProject = project;
        myPid = pid;
        mySdk = sdk;
        if (mySdk != null) {
            mySdkPath = sdk.getHomePath();
        }
        else {
            mySdkPath = null;
        }
    }

    public XDebugSession launch() throws ExecutionException {
        WriteAction.run(() -> FileDocumentManager.getInstance().saveAllDocuments());

        return launchRemoteDebugServer();
    }

    private XDebugSession launchRemoteDebugServer() throws ExecutionException {
        final ServerSocket serverSocket;
        try {
            //noinspection SocketOpenedButNotSafelyClosed
            serverSocket = new ServerSocket(0);
        }
        catch (IOException e) {
            throw new ExecutionException(PyLocalize.debuggerAttachToProcessFailedToFindFreeSocketPort().get(), e);
        }


        PyAttachToProcessCommandLineState state =
            PyAttachToProcessCommandLineState.create(myProject, mySdk, serverSocket.getLocalPort(), myPid);

        final ExecutionResult result = state.execute(state.getEnvironment().getExecutor(), new CommandLinePatcher[0]);

        //start remote debug server
        XDebugProcessStarter starter = new XDebugProcessStarter() {
            @Override
            public XDebugProcess start(final XDebugSession session) {
                PyRemoteDebugProcess pyDebugProcess =
                    new PyRemoteDebugProcess(session, serverSocket, result.getExecutionConsole(),
                        result.getProcessHandler(), "") {
                        @Override
                        protected void printConsoleInfo() {
                        }

                        @Override
                        public int getConnectTimeout() {
                            // TODO consulo: AdvancedSettings missing, JB registers advancedSetting "python.debugger.attach.timeout" with default 20000
                            return 20000;
                        }

                        @Override
                        protected void detachDebuggedProcess() {
                            handleStop();
                        }

                        @Override
                        protected String getConnectionMessage() {
                            return PyLocalize.pythonDebuggerAttachingToProcessWithPid(myPid).get();
                        }

                        @Override
                        protected String getConnectionTitle() {
                            return PyLocalize.pythonDebuggerAttaching().get();
                        }
                    };
                pyDebugProcess.setPositionConverter(new PyLocalPositionConverter());


                createConsoleCommunication(myProject, result, pyDebugProcess, session);

                return pyDebugProcess;
            }
        };
        // TODO consulo: XDebuggerManager.newSessionBuilder() missing - startSessionAndShowTab() is the equivalent of sessionName(pid).showTab(true)
        return XDebuggerManager.getInstance(myProject).startSessionAndShowTab(String.valueOf(myPid), null, starter);
    }
}
