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
package com.jetbrains.python.rest.run;

import javax.swing.SwingUtilities;

import jakarta.annotation.Nullable;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParamsGroup;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.ProcessHandler;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.run.PythonProcessRunner;

/**
 * User : catherine
 */
public abstract class RestCommandLineState extends PythonCommandLineState
{
	protected final RestRunConfiguration myConfiguration;

	public RestCommandLineState(RestRunConfiguration configuration, ExecutionEnvironment env)
	{
		super(configuration, env);
		myConfiguration = configuration;
	}

	@Override
	protected void buildCommandLineParameters(GeneralCommandLine commandLine)
	{
		ParametersList parametersList = commandLine.getParametersList();
		ParamsGroup exeOptions = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
		assert exeOptions != null;
		exeOptions.addParametersString(myConfiguration.getInterpreterOptions());

		ParamsGroup scriptParameters = parametersList.getParamsGroup(GROUP_SCRIPT);
		assert scriptParameters != null;
		getRunner().addToGroup(scriptParameters, commandLine);
		final String key = getKey();
		if(key != null)
		{
			scriptParameters.addParameter(key);
		}
		scriptParameters.addParameter(getTask());

		final String params = myConfiguration.getParams();
		if(params != null)
		{
			scriptParameters.addParametersString(params);
		}

		if(!StringUtil.isEmptyOrSpaces(myConfiguration.getInputFile()))
		{
			scriptParameters.addParameter(myConfiguration.getInputFile());
		}

		if(!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile()))
		{
			scriptParameters.addParameter(myConfiguration.getOutputFile());
		}

		if(!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory()))
		{
			commandLine.setWorkDirectory(myConfiguration.getWorkingDirectory());
		}
	}

	protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException
	{
		final Runnable afterTask = getAfterTask();
		ProcessHandler processHandler = PythonProcessRunner.createProcess(commandLine, false);
		if(afterTask != null)
		{
			processHandler.addProcessListener(new ProcessAdapter()
			{
				public void processTerminated(ProcessEvent event)
				{
					SwingUtilities.invokeLater(afterTask);
				}
			});
		}
		return processHandler;
	}

	@Nullable
	protected Runnable getAfterTask()
	{
		return null;
	}

	@Nullable
	protected VirtualFile findOutput()
	{
		if(!StringUtil.isEmptyOrSpaces(myConfiguration.getOutputFile()))
		{
			VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myConfiguration.getOutputFile());
			if(virtualFile == null)
			{
				virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myConfiguration.getWorkingDirectory() + myConfiguration.getOutputFile());
			}
			return virtualFile;
		}
		return null;
	}

	protected abstract HelperPackage getRunner();

	protected abstract String getTask();

	@Nullable
	protected abstract String getKey();
}
