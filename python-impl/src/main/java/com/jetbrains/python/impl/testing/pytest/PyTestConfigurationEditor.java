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

package com.jetbrains.python.impl.testing.pytest;

import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PyCommonOptionsFormFactory;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class PyTestConfigurationEditor extends SettingsEditor<PyTestRunConfiguration> implements PyTestRunConfigurationParams {
    private final Project myProject;
    private final PyTestRunConfiguration myConfiguration;
    private @Nullable Form myForm;

    public PyTestConfigurationEditor(Project project, PyTestRunConfiguration configuration) {
        myProject = project;
        myConfiguration = configuration;
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
    protected void resetEditorFrom(PyTestRunConfiguration s) {
        Form form = myForm;
        if (form == null) {
            return;
        }

        AbstractPythonRunConfiguration.copyParams(s, form.myCommonOptionsForm);
        form.myKeywordsTextField.setValue(StringUtil.notNullize(s.getKeywords()));
        form.myTestScriptTextField.setValue(StringUtil.notNullize(s.getTestToRun()));
        form.setUseKeyword(s.useKeyword());
        form.setUseParam(s.useParam());
        form.myParamsTextField.setValue(StringUtil.notNullize(s.getParams()));
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(PyTestRunConfiguration s) throws ConfigurationException {
        Form form = myForm;
        if (form == null) {
            return;
        }

        AbstractPythonRunConfiguration.copyParams(form.myCommonOptionsForm, s);
        s.setTestToRun(form.myTestScriptTextField.getValue().trim());
        s.setKeywords(StringUtil.notNullize(form.myKeywordsTextField.getValue()).trim());
        s.setParams(StringUtil.notNullize(form.myParamsTextField.getValue()).trim());
        s.useKeyword(form.myKeywordsCheckBox.getValueOrError());
        s.useParam(form.myParametersCheckBox.getValueOrError());
    }

    @Override
    protected void disposeEditor() {
        myForm = null;
    }

    @Override
    public boolean useParam() {
        Form form = myForm;
        return form != null && form.myParametersCheckBox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void useParam(boolean useParam) {
        Form form = myForm;
        if (form != null) {
            form.setUseParam(useParam);
        }
    }

    @Override
    public boolean useKeyword() {
        Form form = myForm;
        return form != null && form.myKeywordsCheckBox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void useKeyword(boolean useKeyword) {
        Form form = myForm;
        if (form != null) {
            form.setUseKeyword(useKeyword);
        }
    }

    private final class Form {
        private final AbstractPyCommonOptionsForm myCommonOptionsForm;
        private final FileChooserTextBoxBuilder.Controller myTestScriptTextField;
        private final CheckBox myKeywordsCheckBox;
        private final TextBox myKeywordsTextField;
        private final CheckBox myParametersCheckBox;
        private final TextBox myParamsTextField;
        private final VerticalLayout myRootPanel;

        @RequiredUIAccess
        private Form() {
            myCommonOptionsForm = myProject.getApplication()
                .getInstance(PyCommonOptionsFormFactory.class)
                .createForm(myConfiguration.getCommonOptionsFormData(), PyTestConfigurationEditor.this);

            myTestScriptTextField = FileChooserTextBoxBuilder.create(myProject)
                .uiDisposable(PyTestConfigurationEditor.this)
                .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
                .dialogTitle(PyLocalize.runcfgUnittestDlgSelectScriptPath())
                .build();
            myTestScriptTextField.getComponent().setToolTipText(PyLocalize.runcfgPytestTargetTooltip());

            myKeywordsCheckBox = CheckBox.create(PyLocalize.runcfgPytestKeywords());
            myKeywordsTextField = TextBox.create();
            myKeywordsTextField.setToolTipText(PyLocalize.runcfgPytestKeywordsTooltip());

            myParametersCheckBox = CheckBox.create(PyLocalize.runcfgPytestParameters());
            myParamsTextField = TextBox.create();
            myParamsTextField.setToolTipText(PyLocalize.runcfgPytestParametersTooltip());

            FormBuilder builder = FormBuilder.create();
            builder.addLabeled(PyLocalize.runcfgPytestTarget(), myTestScriptTextField.getComponent());
            builder.addLabeled(myKeywordsCheckBox, myKeywordsTextField);
            builder.addLabeled(myParametersCheckBox, myParamsTextField);

            myRootPanel = VerticalLayout.create();
            myRootPanel.add(LabeledLayout.create(PyLocalize.runcfgPytestTestsTitle(), builder.build()));
            myRootPanel.add(myCommonOptionsForm.getMainPanel());

            myKeywordsCheckBox.addValueListener(event -> myKeywordsTextField.setEnabled(Boolean.TRUE.equals(event.getValue())));
            myParametersCheckBox.addValueListener(event -> myParamsTextField.setEnabled(Boolean.TRUE.equals(event.getValue())));

            setUseParam(myConfiguration.useParam());
            setUseKeyword(myConfiguration.useKeyword());
        }

        @RequiredUIAccess
        private void setUseParam(boolean useParam) {
            myParametersCheckBox.setValue(useParam);
            myParamsTextField.setEnabled(useParam);
        }

        @RequiredUIAccess
        private void setUseKeyword(boolean useKeyword) {
            myKeywordsCheckBox.setValue(useKeyword);
            myKeywordsTextField.setEnabled(useKeyword);
        }
    }
}
