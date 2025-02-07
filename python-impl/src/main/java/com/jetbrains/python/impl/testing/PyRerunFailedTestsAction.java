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
package com.jetbrains.python.impl.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.execution.test.AbstractTestProxy;
import consulo.execution.test.action.AbstractRerunFailedTestsAction;
import consulo.process.ExecutionException;
import consulo.execution.ExecutionResult;
import consulo.execution.executor.Executor;
import consulo.execution.action.Location;
import consulo.process.cmd.GeneralCommandLine;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.TestFrameworkRunningModel;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.ComponentContainer;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.CommandLinePatcher;

public class PyRerunFailedTestsAction extends AbstractRerunFailedTestsAction
{
	protected PyRerunFailedTestsAction(@Nonnull ComponentContainer componentContainer)
	{
		super(componentContainer);
	}

	@Override
	@Nullable
	protected MyRunProfile getRunProfile(@Nonnull ExecutionEnvironment environment)
	{
		final TestFrameworkRunningModel model = getModel();
		if(model == null)
		{
			return null;
		}
		return new MyTestRunProfile((AbstractPythonRunConfiguration) model.getProperties().getConfiguration());
	}

	private class MyTestRunProfile extends MyRunProfile
	{

		public MyTestRunProfile(RunConfigurationBase configuration)
		{
			super(configuration);
		}

		@Nonnull
		@Override
		public Module[] getModules()
		{
			return ((AbstractPythonRunConfiguration) getPeer()).getModules();
		}

		@Nullable
		@Override
		public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException
		{
			final AbstractPythonRunConfiguration configuration = ((AbstractPythonRunConfiguration) getPeer());

			// If configuration wants to take care about rerun itself
			if(configuration instanceof TestRunConfigurationReRunResponsible)
			{
				// TODO: Extract method
				final Set<PsiElement> failedTestElements = new HashSet<>();
				for(final AbstractTestProxy proxy : getFailedTests(getProject()))
				{
					final Location<?> location = proxy.getLocation(getProject(), GlobalSearchScope.allScope(getProject()));
					if(location != null)
					{
						failedTestElements.add(location.getPsiElement());
					}
				}
				return ((TestRunConfigurationReRunResponsible) configuration).rerunTests(executor, env, failedTestElements);
			}
			return new FailedPythonTestCommandLineStateBase(configuration, env, (PythonTestCommandLineStateBase) configuration.getState(executor, env));
		}
	}

	private class FailedPythonTestCommandLineStateBase extends PythonTestCommandLineStateBase
	{

		private final PythonTestCommandLineStateBase myState;
		private final Project myProject;

		public FailedPythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration, ExecutionEnvironment env, PythonTestCommandLineStateBase state)
		{
			super(configuration, env);
			myState = state;
			myProject = configuration.getProject();
		}

		@Override
		protected HelperPackage getRunner()
		{
			return myState.getRunner();
		}

		@Override
		public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException
		{
			// Insane rerun tests with out of spec.
			if(getTestSpecs().isEmpty())
			{
				throw new ExecutionException(PyBundle.message("runcfg.tests.cant_rerun"));
			}
			return super.execute(executor, patchers);
		}

		@Nonnull
		@Override
		protected List<String> getTestSpecs()
		{
			List<String> specs = new ArrayList<>();
			List<AbstractTestProxy> failedTests = getFailedTests(myProject);
			for(AbstractTestProxy failedTest : failedTests)
			{
				if(failedTest.isLeaf())
				{
					final Location<?> location = failedTest.getLocation(myProject, myConsoleProperties.getScope());
					if(location != null)
					{
						final String spec = getConfiguration().getTestSpec(location, failedTest);
						if(spec != null && !specs.contains(spec))
						{
							specs.add(spec);
						}
					}
				}
			}
			if(specs.isEmpty())
			{
				final List<String> locations = failedTests.stream().map(AbstractTestProxy::getLocationUrl).collect(Collectors.toList());
				Logger.getInstance(FailedPythonTestCommandLineStateBase.class).warn(String.format("Can't resolve specs for the following tests: %s", StringUtil.join(locations, ", ")));
			}
			return specs;
		}

		@Override
		protected void addAfterParameters(GeneralCommandLine cmd)
		{
			myState.addAfterParameters(cmd);
		}

		@Override
		protected void addBeforeParameters(GeneralCommandLine cmd)
		{
			myState.addBeforeParameters(cmd);
		}

		@Override
		public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs)
		{
			myState.customizeEnvironmentVars(envs, passParentEnvs);
		}
	}
}
