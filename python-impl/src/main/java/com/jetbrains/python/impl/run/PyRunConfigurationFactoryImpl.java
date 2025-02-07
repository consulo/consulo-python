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

package com.jetbrains.python.impl.run;

import com.jetbrains.python.run.PyRunConfigurationFactory;
import com.jetbrains.python.run.PythonRunConfigurationParams;
import consulo.annotation.component.ServiceImpl;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ServiceImpl
@Singleton
public class PyRunConfigurationFactoryImpl extends PyRunConfigurationFactory {
  @Override
  public PythonRunConfigurationParams createPythonScriptRunConfiguration(Module module, String scriptName, boolean singleton) {
    RunnerAndConfigurationSettings settings = createRunConfiguration(module, PythonConfigurationType.getInstance().getFactory());
    settings.setSingleton(singleton);
    PythonRunConfigurationParams configuration = (PythonRunConfigurationParams)settings.getConfiguration();
    configuration.setScriptName(scriptName);
    return configuration;
  }

  @Override
  public RunnerAndConfigurationSettings createRunConfiguration(Module module, ConfigurationFactory factory) {
    final Project project = module.getProject();
    final RunManager runManager = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings settings = createConfigurationSettings(factory, module);
    runManager.addConfiguration(settings, false);
    runManager.setSelectedConfiguration(settings);
    return settings;
  }

  private static RunnerAndConfigurationSettings createConfigurationSettings(ConfigurationFactory factory, @Nonnull final Module module) {
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(module.getProject()).createRunConfiguration(module.getName(), factory);
    ModuleBasedConfiguration configuration = (ModuleBasedConfiguration)settings.getConfiguration();
    configuration.setModule(module);
    return settings;
  }
}
