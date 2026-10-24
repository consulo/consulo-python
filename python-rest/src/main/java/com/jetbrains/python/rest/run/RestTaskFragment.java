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
package com.jetbrains.python.rest.run;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.reStructuredText.localize.RestLocalize;
import consulo.ui.ComboBox;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.lang.StringUtil;

import java.util.List;

/**
 * The docutils or sphinx command with its input, output and options.
 */
public class RestTaskFragment implements RunConfigurationFragment<RestRunConfiguration> {
    private final List<String> myTasks;
    private final String myDefaultTask;
    private final boolean myFolders;

    public RestTaskFragment(List<String> tasks, String defaultTask, boolean folders) {
        myTasks = tasks;
        myDefaultTask = defaultTask;
        myFolders = folders;
    }

    @Override
    public String getId() {
        return "restTask";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentRestTask();
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
    public RunConfigurationFragmentEditor<RestRunConfiguration> createEditor(Project project, Disposable parent) {
        MutableFlatDataModel<String> taskModel = FlatDataModel.of(myTasks);
        ComboBox<String> task = ComboBox.create(taskModel);
        task.setTextRenderer(value -> value == null ? LocalizeValue.empty() : LocalizeValue.of(value));

        FileChooserTextBoxBuilder.Controller input = createFileField(project, parent);
        FileChooserTextBoxBuilder.Controller output = createFileField(project, parent);
        TextBox params = TextBox.create();

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(RestLocalize.runcfgDocutilsCommand(), task);
                layout.addLabeled(RestLocalize.runcfgDocutilsInput(), input.getComponent());
                layout.addLabeled(RestLocalize.runcfgDocutilsOutput(), output.getComponent());
                layout.addLabeled(RestLocalize.runcfgDocutilsOptions(), params);
            }

            @RequiredUIAccess
            @Override
            public void reset(RestRunConfiguration target) {
                String value = StringUtil.isEmptyOrSpaces(target.getTask()) ? myDefaultTask : target.getTask();
                if (taskModel.indexOf(value) < 0) {
                    taskModel.add(value);
                }
                task.setValue(value, false);
                input.setValue(target.getInputFile(), false);
                output.setValue(target.getOutputFile(), false);
                params.setValue(target.getParams(), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(RestRunConfiguration target) {
                String value = task.getValue();
                if (value != null) {
                    target.setTask(value);
                }
                target.setInputFile(StringUtil.notNullize(input.getValue()).trim());
                target.setOutputFile(StringUtil.notNullize(output.getValue()).trim());
                target.setParams(StringUtil.notNullize(params.getValue()).trim());
            }
        };
    }

    @RequiredUIAccess
    private FileChooserTextBoxBuilder.Controller createFileField(Project project, Disposable parent) {
        FileChooserDescriptor descriptor = myFolders
            ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
            : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        return FileChooserTextBoxBuilder.create(project)
            .uiDisposable(parent)
            .fileChooserDescriptor(descriptor)
            .dialogTitle(RestLocalize.runcfgDlgSelectScriptPath())
            .build();
    }
}
