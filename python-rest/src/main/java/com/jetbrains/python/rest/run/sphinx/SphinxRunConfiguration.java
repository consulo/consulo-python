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

package com.jetbrains.python.rest.run.sphinx;

import com.jetbrains.python.rest.run.RestConfigurationEditor;
import com.jetbrains.python.rest.run.RestRunConfiguration;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

/**
 * User : catherine
 */
public class SphinxRunConfiguration extends RestRunConfiguration {
  public SphinxRunConfiguration(final Project project,
                                final ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    RestConfigurationEditor editor = new RestConfigurationEditor(getProject(), this, new SphinxTasksModel());
    editor.setConfigurationName("Sphinx task");
    editor.setOpenInBrowserVisible(false);
    editor.setInputDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor());
    editor.setOutputDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor());
    return editor;
  }

  @Override
  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
    return new SphinxCommandLineState(this, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException
  {
    super.checkConfiguration();
    if (StringUtil.isEmptyOrSpaces(getInputFile()))
      throw new RuntimeConfigurationError("Please specify input directory name.");
    if (StringUtil.isEmptyOrSpaces(getOutputFile()))
      throw new RuntimeConfigurationError("Please specify output directory name.");
  }

  @Override
  public String suggestedName() {
    return "sphinx task in " + getName();
  }
}
