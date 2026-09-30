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

import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.impl.testing.PythonTestRunConfigurationForm;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationForm implements PythonUnitTestRunConfigurationParams {
    private final CheckBox myIsPureUnittest;

    private final PythonTestRunConfigurationForm myTestRunConfigurationForm;

    @RequiredUIAccess
    public PythonUnitTestRunConfigurationForm(Project project, PythonUnitTestRunConfiguration configuration, Disposable uiDisposable) {
        myTestRunConfigurationForm =
            new PythonTestRunConfigurationForm(project, configuration, uiDisposable, PyLocalize.runcfgUnittestDisplay_name());
        myIsPureUnittest = CheckBox.create(PyLocalize.runcfgUnittestDlgPureUnittest(), configuration.isPureUnittest());

        myTestRunConfigurationForm.addTestTypeListener(this::updatePureUnittestVisibility);
        myTestRunConfigurationForm.getAdditionalPanel().add(myIsPureUnittest);
        myTestRunConfigurationForm.setParamsVisible();
        myTestRunConfigurationForm.getParamCheckBox().setValue(configuration.useParam());

        updatePureUnittestVisibility(myTestRunConfigurationForm.getTestType());
    }

    @RequiredUIAccess
    private void updatePureUnittestVisibility(AbstractPythonTestRunConfiguration.TestType testType) {
        myIsPureUnittest.setVisible(testType != AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION);
    }

    @Override
    public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
        return myTestRunConfigurationForm;
    }

    @Override
    public boolean isPureUnittest() {
        return myIsPureUnittest.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void setPureUnittest(boolean isPureUnittest) {
        myIsPureUnittest.setValue(isPureUnittest);
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

    public Component getPanel() {
        return myTestRunConfigurationForm.getPanel();
    }
}
