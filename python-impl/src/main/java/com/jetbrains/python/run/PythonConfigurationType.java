/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.run;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.python.module.extension.PyModuleExtension;
import consulo.ui.image.Image;
import icons.PythonIcons;

/**
 * @author yole
 */
public class PythonConfigurationType implements ConfigurationType
{
	private final PythonConfigurationFactory myFactory = new PythonConfigurationFactory(this);

	public static PythonConfigurationType getInstance()
	{
		for(ConfigurationType configType : Extensions.getExtensions(CONFIGURATION_TYPE_EP))
		{
			if(configType instanceof PythonConfigurationType)
			{
				return (PythonConfigurationType) configType;
			}
		}
		assert false;
		return null;
	}

	private static class PythonConfigurationFactory extends ConfigurationFactory
	{
		protected PythonConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new PythonRunConfiguration(project, this);
		}
	}

	@Override
	public String getDisplayName()
	{
		return "Python";
	}

	@Override
	public String getConfigurationTypeDescription()
	{
		return "Python run configuration";
	}

	@Override
	public Image getIcon()
	{
		return PythonIcons.Python.Python;
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{myFactory};
	}

	public PythonConfigurationFactory getFactory()
	{
		return myFactory;
	}

	@Override
	@Nonnull
	@NonNls
	public String getId()
	{
		return "PythonConfigurationType";
	}
}
