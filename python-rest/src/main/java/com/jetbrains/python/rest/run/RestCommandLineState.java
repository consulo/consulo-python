// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run;

import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;
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
		String key = getKey();
		if(key != null)
		{
			scriptParameters.addParameter(key);
		}
		scriptParameters.addParameter(getTask());

		String params = myConfiguration.getParams();
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

	// TODO consulo: targets API (buildPythonExecution / startProcess(PythonScriptTargetedCommandLineBuilder)) missing
	@Override
	protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException
	{
		final Runnable afterTask = getAfterTask();
		ProcessHandler processHandler = PythonProcessRunner.createProcess(commandLine, false);
		if(afterTask != null)
		{
			processHandler.addProcessListener(new ProcessAdapter()
			{
				@Override
				public void processTerminated(ProcessEvent event)
				{
					SwingUtilities.invokeLater(afterTask);
				}
			});
		}
		return processHandler;
	}

	protected @Nullable Runnable getAfterTask()
	{
		return null;
	}

	protected @Nullable VirtualFile findOutput()
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

	protected abstract @Nullable String getKey();
}
