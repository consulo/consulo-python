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

package com.jetbrains.python.impl.testing.nosetest;

import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.impl.testing.PythonTestRunConfigurationForm;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfigurationForm implements PythonNoseTestRunConfigurationParams {
    private final PythonTestRunConfigurationForm myTestRunConfigurationForm;

    @RequiredUIAccess
    public PythonNoseTestRunConfigurationForm(Project project, PythonNoseTestRunConfiguration configuration, Disposable uiDisposable) {
        myTestRunConfigurationForm =
            new PythonTestRunConfigurationForm(project, configuration, uiDisposable, PyLocalize.runcfgNosetestsDisplay_name());
        myTestRunConfigurationForm.setParamsVisible();
        myTestRunConfigurationForm.getParamCheckBox().setValue(configuration.useParam());
        myTestRunConfigurationForm.setPatternVisible(false);
    }

    @RequiredUIAccess
    @Override
    public String getParams() {
        return myTestRunConfigurationForm.getParams();
    }

    @RequiredUIAccess
    @Override
    public void setParams(String params) {
        myTestRunConfigurationForm.setParams(params);
    }

    @Override
    public boolean useParam() {
        return myTestRunConfigurationForm.getParamCheckBox().getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void useParam(boolean useParam) {
        myTestRunConfigurationForm.getParamCheckBox().setValue(useParam);
    }

    @Override
    public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
        return myTestRunConfigurationForm;
    }

    public Component getPanel() {
        return myTestRunConfigurationForm.getPanel();
    }
}
