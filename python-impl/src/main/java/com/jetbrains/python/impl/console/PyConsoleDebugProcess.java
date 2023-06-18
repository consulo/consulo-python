/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.net.ServerSocket;
import java.util.Map;

import javax.annotation.Nonnull;

import com.google.common.collect.Maps;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.ExecutionConsole;
import consulo.project.Project;
import consulo.ide.impl.idea.remote.RemoteProcessControl;
import consulo.ui.ex.awt.UIUtil;
import consulo.execution.debug.XDebugSession;
import com.jetbrains.python.impl.debugger.PyDebugProcess;
import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;

/**
 * @author traff
 */
public class PyConsoleDebugProcess extends PyDebugProcess
{
	private final int myLocalPort;
	private final PyConsoleDebugProcessHandler myConsoleDebugProcessHandler;

	public PyConsoleDebugProcess(@Nonnull XDebugSession session,
			@Nonnull final ServerSocket serverSocket,
			@Nonnull final ExecutionConsole executionConsole,
			@Nonnull final PyConsoleDebugProcessHandler consoleDebugProcessHandler)
	{
		super(session, serverSocket, executionConsole, consoleDebugProcessHandler, false);
		myLocalPort = serverSocket.getLocalPort();
		myConsoleDebugProcessHandler = consoleDebugProcessHandler;
	}

	@Override
	public void sessionInitialized()
	{
		//nop
	}

	@Override
	protected String getConnectionMessage()
	{
		return "Connecting to console...";
	}

	@Override
	protected String getConnectionTitle()
	{
		return "Debugger connection";
	}

	@Override
	protected void detachDebuggedProcess()
	{
		//TODO: implement disconnect
	}

	@Override
	protected void beforeConnect()
	{
		printToConsole(getCurrentStateMessage() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
	}

	@Override
	protected void afterConnect()
	{
	}


	@Override
	public int getConnectTimeout()
	{
		return 0; //server should not stop
	}

	public void connect(PydevConsoleCommunication consoleCommunication) throws Exception
	{
		int portToConnect;
		if(myConsoleDebugProcessHandler.getConsoleProcessHandler() instanceof consulo.ide.impl.idea.remote.RemoteProcessControl)
		{
			portToConnect = getRemoteTunneledPort(myLocalPort, ((RemoteProcessControl) myConsoleDebugProcessHandler.getConsoleProcessHandler()));
		}
		else
		{
			portToConnect = myLocalPort;
		}
		Map<String, Boolean> optionsMap = makeDebugOptionsMap(getSession());
		Map<String, String> envs = getDebuggerEnvs(getSession());
		consoleCommunication.connectToDebugger(portToConnect, optionsMap, envs);
	}

	private static Map<String, String> getDebuggerEnvs(XDebugSession session)
	{
		Map<String, String> env = Maps.newHashMap();
		PyDebugRunner.configureDebugEnvironment(session.getProject(), env);
		return env;
	}

	private static Map<String, Boolean> makeDebugOptionsMap(XDebugSession session)
	{
		Project project = session.getProject();
		PyDebuggerOptionsProvider userOpts = PyDebuggerOptionsProvider.getInstance(project);
		Map<String, Boolean> dbgOpts = Maps.newHashMap();
		dbgOpts.put("save-signatures", userOpts.isSaveCallSignatures());
		dbgOpts.put("qt-support", userOpts.isSupportQtDebugging());
		return dbgOpts;
	}

	public void waitForNextConnection()
	{
		if(isConnected())
		{
			disconnect();
		}
		if(getSession().isSuspended())
		{
			getSession().resume();
		}
		if(!isWaitingForConnection())
		{
			setWaitingForConnection(true);

			UIUtil.invokeLaterIfNeeded(() -> waitForConnection(getCurrentStateMessage(), getConnectionTitle()));
		}
	}
}
