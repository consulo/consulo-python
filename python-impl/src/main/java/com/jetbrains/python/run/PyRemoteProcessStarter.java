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
package com.jetbrains.python.run;

import com.google.common.net.HostAndPort;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteProcessHandlerBase;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkAdditionalData;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author traff
 */
public class PyRemoteProcessStarter
{
	public static final Key<Boolean> OPEN_FOR_INCOMING_CONNECTION = Key.create("OPEN_FOR_INCOMING_CONNECTION");
	public static final Key<HostAndPort> WEB_SERVER_HOST_AND_PORT = new Key<>("WEB_SERVER_HOST_AND_PORT");

	public ProcessHandler startRemoteProcess(@Nonnull Sdk sdk, @Nonnull GeneralCommandLine commandLine, @Nullable Project project, @Nullable PyRemotePathMapper pathMapper) throws ExecutionException
	{
		PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
		if(manager != null)
		{
			PyRemoteProcessHandlerBase processHandler;

			try
			{
				processHandler = doStartRemoteProcess(sdk, commandLine, manager, project, pathMapper);
			}
			catch(ExecutionException e)
			{
				final Application application = ApplicationManager.getApplication();
				if(application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment()))
				{
					throw new RuntimeException(e);
				}
				throw new ExecutionException("Can't run remote python interpreter: " + e.getMessage(), e);
			}
			ProcessTerminatedListener.attach(processHandler);
			return processHandler;
		}
		else
		{
			throw new PythonRemoteInterpreterManager.PyRemoteInterpreterExecutionException();
		}
	}

	protected PyRemoteProcessHandlerBase doStartRemoteProcess(@Nonnull Sdk sdk,
			@Nonnull final GeneralCommandLine commandLine,
			@Nonnull final PythonRemoteInterpreterManager manager,
			@Nullable final Project project,
			@Nullable PyRemotePathMapper pathMapper) throws ExecutionException
	{
		SdkAdditionalData data = sdk.getSdkAdditionalData();
		assert data instanceof PyRemoteSdkAdditionalDataBase;
		final PyRemoteSdkAdditionalDataBase pyRemoteSdkAdditionalDataBase = (PyRemoteSdkAdditionalDataBase) data;

		final PyRemotePathMapper extendedPathMapper = manager.setupMappings(project, pyRemoteSdkAdditionalDataBase, pathMapper);

		try
		{
			return PyRemoteProcessStarterManagerUtil.getManager(pyRemoteSdkAdditionalDataBase).startRemoteProcess(project, commandLine, manager, pyRemoteSdkAdditionalDataBase, extendedPathMapper);
		}
		catch(InterruptedException e)
		{
			throw new ExecutionException(e);
		}
	}
}
