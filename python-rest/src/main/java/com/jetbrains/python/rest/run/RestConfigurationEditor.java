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

package com.jetbrains.python.rest.run;

import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PyCommonOptionsFormFactory;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.reStructuredText.localize.RestLocalize;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Users : catherine
 */
public class RestConfigurationEditor extends SettingsEditor<RestRunConfiguration> {
    private static final Set<String> TASKS_WITHOUT_BROWSER = Set.of("rst2latex", "rst2odt");

    private final Project myProject;
    private final AbstractPythonRunConfiguration myConfiguration;
    private final List<String> myTaskNames;
    private final String myDefaultTask;

    private LocalizeValue myConfigurationName = LocalizeValue.empty();
    private boolean myOpenInBrowserVisible = true;
    private FileChooserDescriptor myInputDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    private FileChooserDescriptor myOutputDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();

    private @Nullable Form myForm;

    public RestConfigurationEditor(Project project, AbstractPythonRunConfiguration configuration, List<String> tasks, String defaultTask) {
        myProject = project;
        myConfiguration = configuration;
        myTaskNames = tasks;
        myDefaultTask = defaultTask;
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        Form form = new Form();
        myForm = form;
        return form.myRootPanel;
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(RestRunConfiguration configuration) {
        Form form = myForm;
        if (form == null) {
            return;
        }

        AbstractPythonRunConfiguration.copyParams(configuration, form.myCommonOptionsForm);
        form.myInputFileField.setValue(StringUtil.notNullize(configuration.getInputFile()));
        form.myOutputFileField.setValue(StringUtil.notNullize(configuration.getOutputFile()));
        form.myParamsTextField.setValue(StringUtil.notNullize(configuration.getParams()));
        form.selectTask(configuration.getTask());
        form.myOpenInBrowser.setValue(configuration.openInBrowser());
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(RestRunConfiguration configuration) throws ConfigurationException {
        Form form = myForm;
        if (form == null) {
            return;
        }

        AbstractPythonRunConfiguration.copyParams(form.myCommonOptionsForm, configuration);
        configuration.setInputFile(form.myInputFileField.getValue().trim());
        configuration.setOutputFile(form.myOutputFileField.getValue().trim());
        configuration.setParams(StringUtil.notNullize(form.myParamsTextField.getValue()).trim());
        String task = form.myTasks.getValue();
        if (task != null) {
            configuration.setTask(task);
        }

        configuration.setOpenInBrowser(form.myOpenInBrowser.getValueOrError() && form.myOpenInBrowser.isEnabled());
    }

    @Override
    protected void disposeEditor() {
        myForm = null;
    }

    public void setOpenInBrowserVisible(boolean visible) {
        myOpenInBrowserVisible = visible;
    }

    public void setInputDescriptor(FileChooserDescriptor descriptor) {
        myInputDescriptor = descriptor;
    }

    public void setOutputDescriptor(FileChooserDescriptor descriptor) {
        myOutputDescriptor = descriptor;
    }

    public void setConfigurationName(LocalizeValue name) {
        myConfigurationName = name;
    }

    private final class Form {
        private final AbstractPyCommonOptionsForm myCommonOptionsForm;
        private final MutableFlatDataModel<String> myTaskModel;
        private final ComboBox<String> myTasks;
        private final FileChooserTextBoxBuilder.Controller myInputFileField;
        private final FileChooserTextBoxBuilder.Controller myOutputFileField;
        private final TextBox myParamsTextField;
        private final CheckBox myOpenInBrowser;
        private final Component myRootPanel;

        @RequiredUIAccess
        private Form() {
            myCommonOptionsForm = myProject.getApplication()
                .getInstance(PyCommonOptionsFormFactory.class)
                .createForm(myConfiguration.getCommonOptionsFormData(), RestConfigurationEditor.this);

            myTaskModel = FlatDataModel.of(myTaskNames);
            myTasks = ComboBox.create(myTaskModel);
            myTasks.setTextRenderer(task -> task == null ? LocalizeValue.empty() : LocalizeValue.of(task));

            myInputFileField = FileChooserTextBoxBuilder.create(myProject)
                .uiDisposable(RestConfigurationEditor.this)
                .fileChooserDescriptor(myInputDescriptor)
                .dialogTitle(RestLocalize.runcfgDlgSelectScriptPath())
                .build();

            myOutputFileField = FileChooserTextBoxBuilder.create(myProject)
                .uiDisposable(RestConfigurationEditor.this)
                .fileChooserDescriptor(myOutputDescriptor)
                .dialogTitle(RestLocalize.runcfgDlgSelectScriptPath())
                .build();

            myParamsTextField = TextBox.create();

            myOpenInBrowser = CheckBox.create(PyLocalize.runcfgRestOpenInBrowser());
            myOpenInBrowser.setVisible(myOpenInBrowserVisible);

            FormBuilder builder = FormBuilder.create();
            builder.addLabeled(RestLocalize.runcfgDocutilsCommand(), myTasks);
            builder.addLabeled(RestLocalize.runcfgDocutilsInput(), myInputFileField.getComponent());
            builder.addLabeled(RestLocalize.runcfgDocutilsOutput(), myOutputFileField.getComponent());
            builder.addLabeled(RestLocalize.runcfgDocutilsOptions(), myParamsTextField);
            builder.addBottom(myOpenInBrowser);
            builder.addBottom(myCommonOptionsForm.getMainPanel());

            Component content = builder.build();
            myRootPanel = myConfigurationName.isEmpty() ? content : LabeledLayout.create(myConfigurationName, content);

            myTasks.addValueListener(event -> updateOpenInBrowser(event.getValue()));

            selectTask(myDefaultTask);
        }

        @RequiredUIAccess
        private void selectTask(@Nullable String task) {
            String value = StringUtil.isEmptyOrSpaces(task) ? myDefaultTask : task;
            if (myTaskModel.indexOf(value) < 0) {
                myTaskModel.add(value);
            }
            myTasks.setValue(value);
            updateOpenInBrowser(value);
        }

        @RequiredUIAccess
        private void updateOpenInBrowser(@Nullable String task) {
            myOpenInBrowser.setEnabled(task == null || !TASKS_WITHOUT_BROWSER.contains(task));
        }
    }
}
