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
package com.jetbrains.python.impl.testing.pytest;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;

public class PyTestTargetFragment implements RunConfigurationFragment<PyTestRunConfiguration> {
    @Override
    public String getId() {
        return "pyTestTarget";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentPytestTarget();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.FIRST;
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<PyTestRunConfiguration> createEditor(Project project, Disposable parent) {
        FileChooserTextBoxBuilder.Controller target = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(parent)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
            .dialogTitle(PyLocalize.runcfgUnittestDlgSelectScriptPath())
            .build();
        target.getComponent().setToolTipText(PyLocalize.runcfgPytestTargetTooltip());

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(PyLocalize.runcfgPytestTarget(), target.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(PyTestRunConfiguration configuration) {
                target.setValue(configuration.getTestToRun(), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(PyTestRunConfiguration configuration) {
                configuration.setTestToRun(StringUtil.notNullize(target.getValue()).trim());
            }
        };
    }
}
