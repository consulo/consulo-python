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
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationEditor extends SettingsEditor<PythonUnitTestRunConfiguration> {
    private final Project myProject;
    private final PythonUnitTestRunConfiguration myConfiguration;
    private @Nullable PythonUnitTestRunConfigurationForm myForm;

    public PythonUnitTestRunConfigurationEditor(Project project, PythonUnitTestRunConfiguration configuration) {
        myProject = project;
        myConfiguration = configuration;
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        PythonUnitTestRunConfigurationForm form = new PythonUnitTestRunConfigurationForm(myProject, myConfiguration, this);
        myForm = form;
        return form.getPanel();
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(PythonUnitTestRunConfiguration config) {
        PythonUnitTestRunConfigurationForm form = myForm;
        if (form != null) {
            PythonUnitTestRunConfiguration.copyParams(config, form);
        }
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(PythonUnitTestRunConfiguration config) throws ConfigurationException {
        PythonUnitTestRunConfigurationForm form = myForm;
        if (form != null) {
            PythonUnitTestRunConfiguration.copyParams(form, config);
        }
    }

    @Override
    protected void disposeEditor() {
        myForm = null;
    }
}
