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
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.ProgramParametersAware;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
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
import consulo.virtualFileSystem.VirtualFile;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfiguration extends AbstractPythonRunConfiguration implements AbstractPythonRunConfigurationParams, PythonRunConfigurationParams, RefactoringListenerProvider,
		ProgramParametersAware
{

	protected PythonRunConfiguration(Project project, ConfigurationFactory configurationFactory)
	{
		super(project, configurationFactory);
		setUnbufferedEnv();
	}

	@Override
	protected Class<? extends RunConfigurationOptions> getOptionsClass()
	{
		return PythonRunConfigurationOptions.class;
	}

	@Override
	public PythonRunConfigurationOptions getOptions()
	{
		return (PythonRunConfigurationOptions) super.getOptions();
	}

	@Override
	protected void convertLegacyState(LegacyRunConfigurationConverter converter)
	{
		super.convertLegacyState(converter);

		PythonRunConfigurationOptions options = new PythonRunConfigurationOptions();
		convertLegacyCommonState(converter, options);

		PythonRunConfigurationLegacyState old = converter.readXml(PythonRunConfigurationLegacyState.class);
		options.setScriptName(old.SCRIPT_NAME);
		options.setShowCommandLineAfterwards(old.SHOW_COMMAND_LINE);
		converter.setOptions(options);

		converter.addFragment(new ProgramParametersFragmentState(old.PARAMETERS));
	}

	@Override
	public List<RunConfigurationFragment<?>> createFragments()
	{
		return List.of(
			new PythonScriptFragment(),
			new PythonInterpreterFragment<>(),
			new PythonInterpreterOptionsFragment<>(),
			new PythonAddContentRootsFragment<>(),
			new PythonAddSourceRootsFragment<>(),
			new PythonShowCommandLineFragment()
		);
	}

	public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException
	{
		return new PythonScriptCommandLineState(this, env);
	}

	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();

		if(StringUtil.isEmptyOrSpaces(getScriptName()))
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

	@Override
	public String getScriptName()
	{
		return getOptions().getScriptName();
	}

	@Override
	public void setScriptName(String scriptName)
	{
		getOptions().setScriptName(scriptName);
	}

	@Override
	public String getScriptParameters()
	{
		return getProgramParameters();
	}

	@Override
	public void setScriptParameters(String scriptParameters)
	{
		setProgramParameters(scriptParameters);
	}

	@Override
	public boolean showCommandLineAfterwards()
	{
		return getOptions().isShowCommandLineAfterwards();
	}

	@Override
	public void setShowCommandLineAfterwards(boolean showCommandLineAfterwards)
	{
		getOptions().setShowCommandLineAfterwards(showCommandLineAfterwards);
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
			if(virtualFile != null && Comparing.equal(new File(virtualFile.getPath()).getAbsolutePath(), new File(StringUtil.notNullize(getScriptName())).getAbsolutePath()))
			{
				return new RefactoringElementAdapter()
				{
					@Override
					public void elementRenamedOrMoved(PsiElement newElement)
					{
						VirtualFile virtualFile = ((PsiFile) newElement).getVirtualFile();
						if(virtualFile != null)
						{
							updateScriptName(virtualFile.getPath());
						}
					}

					@Override
					public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName)
					{
						updateScriptName(oldQualifiedName);
					}

					private void updateScriptName(String path)
					{
						setScriptName(FileUtil.toSystemDependentName(path));
					}
				};
			}
		}
		return null;
	}
}
