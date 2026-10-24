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
package com.jetbrains.python.impl.run;

import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.project.Project;

/**
 * @author traff
 */
abstract public class AbstractServerPythonRunConfiguration<T extends AbstractServerPythonRunConfiguration> extends AbstractPythonRunConfiguration<T>
{
	public AbstractServerPythonRunConfiguration(Project project, ConfigurationFactory factory)
	{
		super(project, factory);
	}

	@Override
	protected Class<? extends RunConfigurationOptions> getOptionsClass()
	{
		return PythonServerRunConfigurationOptions.class;
	}

	@Override
	public PythonServerRunConfigurationOptions getOptions()
	{
		return (PythonServerRunConfigurationOptions) super.getOptions();
	}

	@Override
	protected void convertLegacyState(LegacyRunConfigurationConverter converter)
	{
		super.convertLegacyState(converter);

		PythonServerRunConfigurationOptions options = new PythonServerRunConfigurationOptions();
		convertLegacyCommonState(converter, options);
		options.setLaunchJavascriptDebugger(converter.readXml(PythonServerRunConfigurationLegacyState.class).launchJavascriptDebuger);
		converter.setOptions(options);
	}

	public boolean isLaunchJavascriptDebugger()
	{
		return getOptions().isLaunchJavascriptDebugger();
	}

	public void setLaunchJavascriptDebugger(boolean launchJavascriptDebugger)
	{
		getOptions().setLaunchJavascriptDebugger(launchJavascriptDebugger);
	}
}
