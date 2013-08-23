package org.consulo.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.consulo.python.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 23.08.13.
 */
public class PythonRunConfigurationType implements ConfigurationType
{
	private static class PythonConfigurationFactory extends ConfigurationFactory
	{
		private PythonConfigurationFactory(ConfigurationType configurationType)
		{
			super(configurationType);
		}

		@Override
		public RunConfiguration createTemplateConfiguration(Project project) {
			return new PythonRunConfiguration(project, this, "");
		}
	}

	@Override
	public String getDisplayName() {
		return "Python";
	}

	@Override
	public String getConfigurationTypeDescription() {
		return "Python run configuration";
	}

	@Override
	public Icon getIcon() {
		return PythonIcons.PythonRun;
	}

	@NotNull
	@Override
	public String getId() {
		return "python.run";
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories() {
		return new ConfigurationFactory[] {new PythonConfigurationFactory(this)};
	}
}
