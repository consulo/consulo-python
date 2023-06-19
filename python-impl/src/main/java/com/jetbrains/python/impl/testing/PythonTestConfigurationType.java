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

package com.jetbrains.python.impl.testing;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.PythonIcons;
import com.jetbrains.python.impl.testing.attest.PythonAtTestRunConfiguration;
import com.jetbrains.python.impl.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.impl.testing.nosetest.PythonNoseTestRunConfiguration;
import com.jetbrains.python.impl.testing.pytest.PyTestRunConfiguration;
import com.jetbrains.python.impl.testing.unittest.PythonUnitTestRunConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeUtil;
import consulo.execution.configuration.RunConfiguration;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.python.module.extension.PyModuleExtension;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

/**
 * User : catherine
 */
@ExtensionImpl
public class PythonTestConfigurationType implements ConfigurationType
{
	public static final String ID = "PythonTestConfigurationType";

	public final PythonDocTestConfigurationFactory PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);
	public final PythonUnitTestConfigurationFactory PY_UNITTEST_FACTORY = new PythonUnitTestConfigurationFactory(this);
	public final PythonNoseTestConfigurationFactory PY_NOSETEST_FACTORY = new PythonNoseTestConfigurationFactory(this);
	public final PythonPyTestConfigurationFactory PY_PYTEST_FACTORY = new PythonPyTestConfigurationFactory(this);
	public final PythonAtTestConfigurationFactory PY_ATTEST_FACTORY = new PythonAtTestConfigurationFactory(this);

	public static PythonTestConfigurationType getInstance()
	{
		return ConfigurationTypeUtil.findConfigurationType(PythonTestConfigurationType.class);
	}

	private static class PythonUnitTestConfigurationFactory extends ConfigurationFactory
	{
		protected PythonUnitTestConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new PythonUnitTestRunConfiguration(project, this);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
		}

		@Override
		public String getName()
		{
			return PyBundle.message("runcfg.unittest.display_name");
		}
	}

	private static class PythonDocTestConfigurationFactory extends ConfigurationFactory
	{
		protected PythonDocTestConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new PythonDocTestRunConfiguration(project, this);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
		}

		@Override
		public String getName()
		{
			return PyBundle.message("runcfg.doctest.display_name");
		}
	}

	private static class PythonPyTestConfigurationFactory extends ConfigurationFactory
	{
		protected PythonPyTestConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new PyTestRunConfiguration(project, this);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
		}

		@Override
		public String getName()
		{
			return PyBundle.message("runcfg.pytest.display_name");
		}
	}

	private static class PythonNoseTestConfigurationFactory extends ConfigurationFactory
	{
		protected PythonNoseTestConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new PythonNoseTestRunConfiguration(project, this);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
		}

		@Override
		public String getName()
		{
			return PyBundle.message("runcfg.nosetests.display_name");
		}
	}

	private static class PythonAtTestConfigurationFactory extends ConfigurationFactory
	{
		protected PythonAtTestConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new PythonAtTestRunConfiguration(project, this);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
		}

		@Override
		public String getName()
		{
			return PyBundle.message("runcfg.attest.display_name");
		}
	}

	@Override
	public String getDisplayName()
	{
		return PyBundle.message("runcfg.test.display_name");
	}

	@Override
	public String getConfigurationTypeDescription()
	{
		return PyBundle.message("runcfg.test.description");
	}

	@Override
	public Image getIcon()
	{
		return PythonIcons.Python.PythonTests;
	}

	@Nonnull
	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{
				PY_UNITTEST_FACTORY,
				PY_DOCTEST_FACTORY,
				PY_NOSETEST_FACTORY,
				PY_PYTEST_FACTORY,
				PY_ATTEST_FACTORY
		};
	}
}
