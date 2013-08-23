/*
 * Copyright 2006 Dmitry Jemerov (yole)
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
package org.consulo.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PythonRunConfiguration extends RunConfigurationBase
{
	public String SCRIPT_NAME;
	public String PARAMETERS;
	public String WORKING_DIRECTORY;

	protected PythonRunConfiguration(Project project, ConfigurationFactory configurationFactory, String name)
	{
		super(project, configurationFactory, name);
	}

	@Override
	public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
		return new PythonRunConfigurationEditor();
	}

	@Nullable
	@Override
	public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
		CommandLineState state = new PythonCommandLineState(executionEnvironment, this);

		TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(getProject());
		consoleBuilder.addFilter(new PythonTracebackFilter(getProject()));
		state.setConsoleBuilder(consoleBuilder);
		return state;
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException {
	}

	@Override
	public void readExternal(Element element) throws InvalidDataException {
		super.readExternal(element);
		DefaultJDOMExternalizer.readExternal(this, element);
	}

	@Override
	public void writeExternal(Element element) throws WriteExternalException {
		super.writeExternal(element);
		DefaultJDOMExternalizer.writeExternal(this, element);
	}
}