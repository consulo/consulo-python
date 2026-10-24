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
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;

public class PythonInterpreterOptionsFragment<T extends AbstractPythonRunConfiguration> implements RunConfigurationFragment<T> {
    @Override
    public String getId() {
        return "pythonInterpreterOptions";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentInterpreterOptions();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.MODULE + 20;
    }

    @Override
    public boolean isVisibleByDefault(T target) {
        return !StringUtil.isEmptyOrSpaces(target.getInterpreterOptions());
    }

    @Override
    public void applyHidden(T target) {
        target.setInterpreterOptions(null);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<T> createEditor(Project project, Disposable parent) {
        TextBoxWithExpandAction options = TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            PyLocalize.runcfgCaptionsInterpreter_options_dialog().get(),
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(PyLocalize.runcfgLabelsInterpreter_options(), options);
            }

            @RequiredUIAccess
            @Override
            public void reset(T target) {
                options.setValue(target.getInterpreterOptions());
            }

            @RequiredUIAccess
            @Override
            public void apply(T target) {
                target.setInterpreterOptions(StringUtil.notNullize(options.getValue()).trim());
            }
        };
    }
}
