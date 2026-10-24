/*
 * Copyright 2013-2026 consulo.io
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

import consulo.disposer.Disposable;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;

/**
 * Extra arguments passed to the test runner of a configuration whose options are {@link PythonTestParamsOptions}.
 */
public class PythonTestParamsFragment<T extends AbstractPythonTestRunConfiguration> implements RunConfigurationFragment<T> {
    @Override
    public String getId() {
        return "pythonTestParams";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentTestParams();
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof AbstractPythonTestRunConfiguration test && test.getOptions() instanceof PythonTestParamsOptions;
    }

    @Override
    public boolean isVisibleByDefault(T target) {
        return params(target).isUseParam();
    }

    @Override
    public void applyHidden(T target) {
        params(target).setUseParam(false);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<T> createEditor(Project project, Disposable parent) {
        TextBox params = TextBox.create();
        params.setToolTipText(PyLocalize.runcfgUnittestDlgParamsTooltip());

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(PyLocalize.runcfgUnittestDlgParams(), params);
            }

            @RequiredUIAccess
            @Override
            public void reset(T target) {
                params.setValue(StringUtil.notNullize(params(target).getParams()));
            }

            @RequiredUIAccess
            @Override
            public void apply(T target) {
                PythonTestParamsOptions options = params(target);
                options.setUseParam(true);
                options.setParams(StringUtil.notNullize(params.getValue()).trim());
            }
        };
    }

    private static PythonTestParamsOptions params(AbstractPythonTestRunConfiguration target) {
        return (PythonTestParamsOptions) target.getOptions();
    }
}
