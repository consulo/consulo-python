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

package com.jetbrains.python.impl.testing.unittest;

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationEditor extends SettingsEditor<PythonUnitTestRunConfiguration> {
  private PythonUnitTestRunConfigurationForm myForm;

  public PythonUnitTestRunConfigurationEditor(final Project project, final PythonUnitTestRunConfiguration configuration) {
    myForm = new PythonUnitTestRunConfigurationForm(project, configuration);
  }

  protected void resetEditorFrom(final PythonUnitTestRunConfiguration config) {
    PythonUnitTestRunConfiguration.copyParams(config, myForm);
  }

  protected void applyEditorTo(final PythonUnitTestRunConfiguration config) throws ConfigurationException {
    PythonUnitTestRunConfiguration.copyParams(myForm, config);
  }

  @Nonnull
  protected JComponent createEditor() {
    return myForm.getPanel();
  }

  protected void disposeEditor() {
    myForm = null;
  }
}
