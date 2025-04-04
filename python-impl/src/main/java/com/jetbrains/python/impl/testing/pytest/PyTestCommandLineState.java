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
package com.jetbrains.python.impl.testing.pytest;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.process.ExecutionException;
import consulo.execution.executor.Executor;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.process.ProcessHandler;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.ConsoleView;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.testing.PythonTestCommandLineStateBase;

/**
 * @author yole
 */
public class PyTestCommandLineState extends PythonTestCommandLineStateBase
{
	private final PyTestRunConfiguration myConfiguration;

	public PyTestCommandLineState(PyTestRunConfiguration configuration, ExecutionEnvironment env)
	{
		super(configuration, env);
		myConfiguration = configuration;
	}

	@Override
	protected void addBeforeParameters(GeneralCommandLine cmd)
	{
		ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
		assert script_params != null;
		script_params.addParameters("-p", "pytest_teamcity");
	}

	@Override
	protected HelperPackage getRunner()
	{
		return PythonHelper.PYTEST;
	}

	@Nonnull
	@Override
	protected List<String> getTestSpecs()
	{
		List<String> specs = new ArrayList<>();
		specs.add(myConfiguration.getTestToRun());
		return specs;
	}

	@Override
	protected void addAfterParameters(GeneralCommandLine cmd)
	{
		ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
		assert script_params != null;
		String params = myConfiguration.getParams();
		if(!StringUtil.isEmptyOrSpaces(params))
		{
			script_params.addParametersString(params);
		}

		String keywords = myConfiguration.getKeywords();
		if(!StringUtil.isEmptyOrSpaces(keywords))
		{
			script_params.addParameter("-k " + keywords);
		}

	}

	@Nonnull
	protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor) throws ExecutionException
	{
		final ConsoleView consoleView = super.createAndAttachConsole(project, processHandler, executor);
		addTracebackFilter(project, consoleView, processHandler);
		return consoleView;
	}
}
