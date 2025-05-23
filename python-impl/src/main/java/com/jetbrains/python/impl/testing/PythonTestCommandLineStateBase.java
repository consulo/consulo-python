/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.testing;

import com.google.common.collect.Lists;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.test.action.ToggleAutoTestAction;
import consulo.execution.test.sm.SMTestRunnerConnectionUtil;
import consulo.execution.test.sm.ui.SMTRunnerConsoleView;
import consulo.process.ExecutionException;
import consulo.execution.ExecutionResult;
import consulo.execution.executor.Executor;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.process.ProcessHandler;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.ui.BaseTestsOutputConsoleView;
import consulo.execution.ui.console.ConsoleView;
import consulo.ui.ex.action.AnAction;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.CommandLinePatcher;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.PythonSdkType;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonTestCommandLineStateBase extends PythonCommandLineState
{
	protected final AbstractPythonRunConfiguration myConfiguration;

	public AbstractPythonRunConfiguration<?> getConfiguration()
	{
		return myConfiguration;
	}

	public PythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration, ExecutionEnvironment env)
	{
		super(configuration, env);
		myConfiguration = configuration;
	}

	@Override
	@Nonnull
	protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor) throws ExecutionException
	{

		final PythonTRunnerConsoleProperties consoleProperties = createConsoleProperties(executor);

		if(isDebug())
		{
			final ConsoleView testsOutputConsoleView = SMTestRunnerConnectionUtil.createConsole(PythonTRunnerConsoleProperties.FRAMEWORK_NAME, consoleProperties);
			final ConsoleView consoleView = new PythonDebugLanguageConsoleView(project, PythonSdkType.findSdkByPath(myConfiguration.getInterpreterPath()), testsOutputConsoleView);
			consoleView.attachToProcess(processHandler);
			addTracebackFilter(project, consoleView, processHandler);
			return consoleView;
		}
		final ConsoleView consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole(PythonTRunnerConsoleProperties.FRAMEWORK_NAME, processHandler, consoleProperties);
		addTracebackFilter(project, consoleView, processHandler);
		return consoleView;
	}

	protected PythonTRunnerConsoleProperties createConsoleProperties(Executor executor)
	{
		return new PythonTRunnerConsoleProperties(myConfiguration, executor, false);
	}

	@Override
	public GeneralCommandLine generateCommandLine()
	{
		GeneralCommandLine cmd = super.generateCommandLine();

		setWorkingDirectory(cmd);

		ParamsGroup exe_options = cmd.getParametersList().getParamsGroup(GROUP_EXE_OPTIONS);
		assert exe_options != null;
		exe_options.addParametersString(myConfiguration.getInterpreterOptions());
		addTestRunnerParameters(cmd);

		return cmd;
	}

	protected void setWorkingDirectory(@Nonnull final GeneralCommandLine cmd)
	{
		final String workingDirectory = myConfiguration.getWorkingDirectory();
		if(!StringUtil.isEmptyOrSpaces(workingDirectory))
		{
			cmd.withWorkDirectory(workingDirectory);
		}
		else if(myConfiguration instanceof AbstractPythonTestRunConfiguration)
		{
			final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration) myConfiguration;
			cmd.withWorkDirectory(configuration.getWorkingDirectorySafe());
		}
	}

	@Override
	public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException
	{
		final ProcessHandler processHandler = startProcess(patchers);
		final ConsoleView console = createAndAttachConsole(myConfiguration.getProject(), processHandler, executor);

		List<AnAction> actions = Lists.newArrayList(createActions(console, processHandler));

		DefaultExecutionResult executionResult = new DefaultExecutionResult(console, processHandler, actions.toArray(new AnAction[actions.size()]));

		PyRerunFailedTestsAction rerunFailedTestsAction = new PyRerunFailedTestsAction(console);
		if(console instanceof SMTRunnerConsoleView)
		{
			rerunFailedTestsAction.init(((BaseTestsOutputConsoleView) console).getProperties());
			rerunFailedTestsAction.setModelProvider(() -> ((SMTRunnerConsoleView) console).getResultsViewer());
		}

		executionResult.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction());
		return executionResult;
	}

	protected void addBeforeParameters(GeneralCommandLine cmd)
	{
	}

	protected void addAfterParameters(GeneralCommandLine cmd)
	{
	}

	protected void addTestRunnerParameters(GeneralCommandLine cmd)
	{
		ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
		assert scriptParams != null;
		getRunner().addToGroup(scriptParams, cmd);
		addBeforeParameters(cmd);
		myConfiguration.addTestSpecsAsParameters(scriptParams, getTestSpecs());
		addAfterParameters(cmd);
	}

	@Override
	public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs)
	{
		super.customizeEnvironmentVars(envs, passParentEnvs);
		envs.put("PYCHARM_HELPERS_DIR", PythonHelpersLocator.getHelperPath("pycharm"));
	}

	protected abstract HelperPackage getRunner();

	@Nonnull
	protected abstract List<String> getTestSpecs();
}
