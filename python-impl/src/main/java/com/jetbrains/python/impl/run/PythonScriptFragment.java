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
package com.jetbrains.python.impl.run;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

public class PythonScriptFragment implements RunConfigurationFragment<PythonRunConfiguration> {
    @Override
    public String getId() {
        return "pythonScript";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentScript();
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
    public RunConfigurationFragmentEditor<PythonRunConfiguration> createEditor(Project project, Disposable parent) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                return file.isDirectory() || file.getExtension() == null || Comparing.equal(file.getExtension(), "py");
            }
        };
        FileChooserTextBoxBuilder.Controller script = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(parent)
            .fileChooserDescriptor(descriptor)
            .dialogTitle(PyLocalize.runcfgLabelsSelectScript())
            .build();

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(PyLocalize.runcfgLabelsScript(), script.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(PythonRunConfiguration target) {
                script.setValue(FileUtil.toSystemDependentName(StringUtil.notNullize(target.getScriptName())), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(PythonRunConfiguration target) {
                target.setScriptName(FileUtil.toSystemIndependentName(StringUtil.notNullize(script.getValue()).trim()));
            }
        };
    }
}
