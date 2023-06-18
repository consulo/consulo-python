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

package com.jetbrains.python.impl.testing.doctest;

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import javax.annotation.Nonnull;

import javax.swing.*;

/**
 * User: catherine
 */
public class PythonDocTestRunConfigurationEditor extends SettingsEditor<PythonDocTestRunConfiguration> {
  private PythonDocTestRunConfigurationForm myForm;

  public PythonDocTestRunConfigurationEditor(final Project project, final PythonDocTestRunConfiguration configuration) {
    myForm = new PythonDocTestRunConfigurationForm(project, configuration);
  }

  protected void resetEditorFrom(final PythonDocTestRunConfiguration config) {
    PythonDocTestRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonDocTestRunConfiguration config) throws ConfigurationException {
    PythonDocTestRunConfiguration.copyParams(myForm, config);
  }

  @Nonnull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
