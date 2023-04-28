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
package com.jetbrains.python.testing.attest;

import org.jdom.Element;
import javax.annotation.Nonnull;
import consulo.process.ExecutionException;
import consulo.execution.executor.Executor;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import consulo.content.bundle.Sdk;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.VFSTestFrameworkListener;

/**
 * User: catherine
 */
public class PythonAtTestRunConfiguration extends AbstractPythonTestRunConfiguration implements PythonAtTestRunConfigurationParams
{
	protected String myTitle = "Attest";
	protected String myPluralTitle = "Attests";

	public PythonAtTestRunConfiguration(Project project, ConfigurationFactory configurationFactory)
	{
		super(project, configurationFactory);
	}

	@Override
	protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor()
	{
		return new PythonAtTestRunConfigurationEditor(getProject(), this);
	}

	@Override
	public RunProfileState getState(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment env) throws ExecutionException
	{
		return new PythonAtTestCommandLineState(this, env);
	}

	@Override
	public void readExternal(Element element) throws InvalidDataException
	{
		super.readExternal(element);
	}

	@Override
	public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);
	}

	@Override
	protected String getTitle()
	{
		return myTitle;
	}

	@Override
	protected String getPluralTitle()
	{
		return myPluralTitle;
	}

	public static void copyParams(PythonAtTestRunConfigurationParams source, PythonAtTestRunConfigurationParams target)
	{
		copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();
		Sdk sdkPath = PythonSdkType.findSdkByPath(getInterpreterPath());
		if(sdkPath != null && !VFSTestFrameworkListener.getInstance().isAtTestInstalled(sdkPath))
		{
			throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "attest"));
		}
	}
}
