/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.run.PythonRunConfigurationParams;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;

import jakarta.annotation.Nonnull;
import java.io.File;

/**
 * @author yole
 */
public class PythonRunConfiguration extends AbstractPythonRunConfiguration implements AbstractPythonRunConfigurationParams, PythonRunConfigurationParams, RefactoringListenerProvider
{
	public static final String SCRIPT_NAME = "SCRIPT_NAME";
	public static final String PARAMETERS = "PARAMETERS";
	public static final String MULTIPROCESS = "MULTIPROCESS";
	public static final String SHOW_COMMAND_LINE = "SHOW_COMMAND_LINE";
	private String myScriptName;
	private String myScriptParameters;
	private boolean myShowCommandLineAfterwards = false;

	protected PythonRunConfiguration(Project project, ConfigurationFactory configurationFactory)
	{
		super(project, configurationFactory);
		setUnbufferedEnv();
	}

	@Override
	protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor()
	{
		return new PythonRunConfigurationEditor(this);
	}

	public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException
	{
		return new PythonScriptCommandLineState(this, env);
	}

	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();

		if(StringUtil.isEmptyOrSpaces(myScriptName))
		{
			throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_script_name"));
		}
	}

	public String suggestedName()
	{
		String scriptName = getScriptName();
		if(scriptName == null)
		{
			return null;
		}
		String name = new File(scriptName).getName();
		if(name.endsWith(".py"))
		{
			return name.substring(0, name.length() - 3);
		}
		return name;
	}

	public String getScriptName()
	{
		return myScriptName;
	}

	public void setScriptName(String scriptName)
	{
		myScriptName = scriptName;
	}

	public String getScriptParameters()
	{
		return myScriptParameters;
	}

	public void setScriptParameters(String scriptParameters)
	{
		myScriptParameters = scriptParameters;
	}

	public boolean showCommandLineAfterwards()
	{
		return myShowCommandLineAfterwards;
	}

	public void setShowCommandLineAfterwards(boolean showCommandLineAfterwards)
	{
		myShowCommandLineAfterwards = showCommandLineAfterwards;
	}

	public void readExternal(Element element)
	{
		super.readExternal(element);
		myScriptName = JDOMExternalizerUtil.readField(element, SCRIPT_NAME);
		myScriptParameters = JDOMExternalizerUtil.readField(element, PARAMETERS);
		myShowCommandLineAfterwards = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, SHOW_COMMAND_LINE, "false"));
	}

	public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		JDOMExternalizerUtil.writeField(element, SCRIPT_NAME, myScriptName);
		JDOMExternalizerUtil.writeField(element, PARAMETERS, myScriptParameters);
		JDOMExternalizerUtil.writeField(element, SHOW_COMMAND_LINE, Boolean.toString(myShowCommandLineAfterwards));
	}

	public AbstractPythonRunConfigurationParams getBaseParams()
	{
		return this;
	}

	public static void copyParams(PythonRunConfigurationParams source, PythonRunConfigurationParams target)
	{
		AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
		target.setScriptName(source.getScriptName());
		target.setScriptParameters(source.getScriptParameters());
		target.setShowCommandLineAfterwards(source.showCommandLineAfterwards());
	}

	@Override
	public RefactoringElementListener getRefactoringElementListener(PsiElement element)
	{
		if(element instanceof PsiFile)
		{
			VirtualFile virtualFile = ((PsiFile) element).getVirtualFile();
			if(virtualFile != null && Comparing.equal(new File(virtualFile.getPath()).getAbsolutePath(), new File(myScriptName).getAbsolutePath()))
			{
				return new RefactoringElementAdapter()
				{
					@Override
					public void elementRenamedOrMoved(@Nonnull PsiElement newElement)
					{
						VirtualFile virtualFile = ((PsiFile) newElement).getVirtualFile();
						if(virtualFile != null)
						{
							updateScriptName(virtualFile.getPath());
						}
					}

					@Override
					public void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName)
					{
						updateScriptName(oldQualifiedName);
					}

					private void updateScriptName(String path)
					{
						myScriptName = FileUtil.toSystemDependentName(path);
					}
				};
			}
		}
		return null;
	}
}
