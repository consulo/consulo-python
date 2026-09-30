/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.impl.run.PyCommonOptionsFormFactory;
import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration.TestType;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.RadioGroup;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.collection.Lists;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Leonid Shalupov
 */
public class PythonTestRunConfigurationForm implements AbstractPythonTestRunConfigurationParams {
    private final AbstractPyCommonOptionsForm myCommonOptionsForm;

    private final RadioGroup<TestType> myTestTypeGroup;
    private final FileChooserTextBoxBuilder.Controller myTestFolderTextField;
    private final CheckBox myPatternCheckBox;
    private final TextBox myPatternTextField;
    private final FileChooserTextBoxBuilder.Controller myTestScriptTextField;
    private final TextBox myTestClassTextField;
    private final Label myTestMethodLabel;
    private final TextBox myTestMethodTextField;
    private final CheckBox myParamCheckBox;
    private final TextBox myParamTextField;

    private final Row myTestFolderRow;
    private final Row myPatternRow;
    private final Row myTestScriptRow;
    private final Row myTestClassRow;
    private final Row myTestMethodRow;
    private final Row myParamRow;

    private final VerticalLayout myAdditionalPanel;
    private final LabeledLayout myTestsPanel;
    private final VerticalLayout myRootPanel;

    private final List<Consumer<TestType>> myTestTypeListeners = Lists.newLockFreeCopyOnWriteList();

    private boolean myPatternIsVisible = true;

    @RequiredUIAccess
    public PythonTestRunConfigurationForm(
        Project project,
        AbstractPythonTestRunConfiguration configuration,
        Disposable uiDisposable,
        LocalizeValue title
    ) {
        myCommonOptionsForm = project.getApplication()
            .getInstance(PyCommonOptionsFormFactory.class)
            .createForm(configuration.getCommonOptionsFormData(), uiDisposable);

        myTestTypeGroup = RadioGroup.create();
        Component testTypes = myTestTypeGroup.fillHorizontal(List.of(TestType.values()), PythonTestRunConfigurationForm::getTestTypeName);

        myTestFolderTextField = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(uiDisposable)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .dialogTitle(PyLocalize.runcfgUnittestDlgSelectFolderPath())
            .build();

        myPatternCheckBox = CheckBox.create(PyLocalize.runcfgUnittestDlgPattern());
        myPatternTextField = TextBox.create();
        myPatternTextField.setToolTipText(PyLocalize.runcfgUnittestDlgPatternTooltip());

        myTestScriptTextField = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(uiDisposable)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(PyLocalize.runcfgUnittestDlgSelectScriptPath())
            .build();

        myTestClassTextField = TextBox.create();

        myTestMethodLabel = Label.create(PyLocalize.runcfgUnittestDlgMethod_label());
        myTestMethodTextField = TextBox.create();

        myParamCheckBox = CheckBox.create(PyLocalize.runcfgUnittestDlgParams());
        myParamTextField = TextBox.create();
        myParamTextField.setToolTipText(PyLocalize.runcfgUnittestDlgParamsTooltip());

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(PyLocalize.runcfgUnittestDlgTest_type_title(), testTypes);
        myTestFolderRow = addRow(builder, Label.create(PyLocalize.runcfgUnittestDlgFolder_path()), myTestFolderTextField.getComponent());
        myPatternRow = addRow(builder, myPatternCheckBox, myPatternTextField);
        myTestScriptRow = addRow(builder, Label.create(PyLocalize.runcfgUnittestDlgTest_script_label()), myTestScriptTextField.getComponent());
        myTestClassRow = addRow(builder, Label.create(PyLocalize.runcfgUnittestDlgClass_label()), myTestClassTextField);
        myTestMethodRow = addRow(builder, myTestMethodLabel, myTestMethodTextField);
        myParamRow = addRow(builder, myParamCheckBox, myParamTextField);

        myAdditionalPanel = VerticalLayout.create();
        myAdditionalPanel.add(builder.build());
        myTestsPanel = LabeledLayout.create(title, myAdditionalPanel);

        myRootPanel = VerticalLayout.create();
        myRootPanel.add(myTestsPanel);
        myRootPanel.add(myCommonOptionsForm.getMainPanel());

        myParamRow.setVisible(false);
        myParamTextField.setEnabled(false);

        myTestTypeGroup.addValueListener(testType -> {
            if (testType != null) {
                onTestTypeChanged(testType);
            }
        });
        myPatternCheckBox.addValueListener(event -> myPatternTextField.setEnabled(Boolean.TRUE.equals(event.getValue())));
        myParamCheckBox.addValueListener(event -> myParamTextField.setEnabled(Boolean.TRUE.equals(event.getValue())));

        usePattern(configuration.usePattern());
        setTestType(configuration.getTestType());
    }

    private static LocalizeValue getTestTypeName(TestType testType) {
        return switch (testType) {
            case TEST_FOLDER -> PyLocalize.runcfgUnittestDlgAll_in_folder_title();
            case TEST_SCRIPT -> PyLocalize.runcfgUnittestDlgAll_in_script_title();
            case TEST_CLASS -> PyLocalize.runcfgUnittestDlgTest_class_title();
            case TEST_METHOD -> PyLocalize.runcfgUnittestDlgTest_method_title();
            case TEST_FUNCTION -> PyLocalize.runcfgUnittestDlgTest_function_title();
        };
    }

    @RequiredUIAccess
    private static Row addRow(FormBuilder builder, Component label, Component field) {
        builder.addLabeled(label, field);
        return new Row(label, field);
    }

    @Override
    public AbstractPythonRunConfigurationParams getBaseParams() {
        return myCommonOptionsForm;
    }

    public Disposable addTestTypeListener(Consumer<TestType> listener) {
        myTestTypeListeners.add(listener);
        return () -> myTestTypeListeners.remove(listener);
    }

    @RequiredUIAccess
    @Override
    public String getClassName() {
        return StringUtil.notNullize(myTestClassTextField.getValue()).trim();
    }

    @RequiredUIAccess
    @Override
    public void setClassName(@Nullable String className) {
        myTestClassTextField.setValue(StringUtil.notNullize(className));
    }

    @RequiredUIAccess
    @Override
    public String getPattern() {
        return StringUtil.notNullize(myPatternTextField.getValue()).trim();
    }

    @RequiredUIAccess
    @Override
    public void setPattern(@Nullable String pattern) {
        myPatternTextField.setValue(StringUtil.notNullize(pattern));
    }

    @Override
    public boolean shouldAddContentRoots() {
        return myCommonOptionsForm.shouldAddContentRoots();
    }

    @Override
    public boolean shouldAddSourceRoots() {
        return myCommonOptionsForm.shouldAddSourceRoots();
    }

    @RequiredUIAccess
    @Override
    public void setAddContentRoots(boolean addContentRoots) {
        myCommonOptionsForm.setAddContentRoots(addContentRoots);
    }

    @RequiredUIAccess
    @Override
    public void setAddSourceRoots(boolean addSourceRoots) {
        myCommonOptionsForm.setAddSourceRoots(addSourceRoots);
    }

    @RequiredUIAccess
    @Override
    public String getFolderName() {
        return FileUtil.toSystemIndependentName(myTestFolderTextField.getValue().trim());
    }

    @RequiredUIAccess
    @Override
    public void setFolderName(@Nullable String folderName) {
        myTestFolderTextField.setValue(FileUtil.toSystemDependentName(StringUtil.notNullize(folderName)));
    }

    @RequiredUIAccess
    @Override
    public String getScriptName() {
        return FileUtil.toSystemIndependentName(myTestScriptTextField.getValue().trim());
    }

    @RequiredUIAccess
    @Override
    public void setScriptName(@Nullable String scriptName) {
        myTestScriptTextField.setValue(FileUtil.toSystemDependentName(StringUtil.notNullize(scriptName)));
    }

    @RequiredUIAccess
    @Override
    public String getMethodName() {
        return StringUtil.notNullize(myTestMethodTextField.getValue()).trim();
    }

    @RequiredUIAccess
    @Override
    public void setMethodName(@Nullable String methodName) {
        myTestMethodTextField.setValue(StringUtil.notNullize(methodName));
    }

    @Override
    public TestType getTestType() {
        TestType testType = myTestTypeGroup.getValue();
        return testType != null ? testType : TestType.TEST_FUNCTION;
    }

    @RequiredUIAccess
    @Override
    public void setTestType(TestType testType) {
        myTestTypeGroup.setValue(testType, false);
        onTestTypeChanged(testType);
    }

    @RequiredUIAccess
    private void onTestTypeChanged(TestType testType) {
        myTestFolderRow.setVisible(testType == TestType.TEST_FOLDER);
        myTestScriptRow.setVisible(testType != TestType.TEST_FOLDER);
        myTestClassRow.setVisible(testType == TestType.TEST_CLASS || testType == TestType.TEST_METHOD);
        myTestMethodRow.setVisible(testType == TestType.TEST_METHOD || testType == TestType.TEST_FUNCTION);
        myTestMethodLabel.setText(
            testType == TestType.TEST_METHOD ? PyLocalize.runcfgUnittestDlgMethod_label() : PyLocalize.runcfgUnittestDlgFunction_label()
        );
        myPatternTextField.setEnabled(myPatternCheckBox.getValueOrError());
        myParamTextField.setEnabled(myParamCheckBox.getValueOrError());
        if (myPatternIsVisible) {
            myPatternRow.setVisible(testType == TestType.TEST_FOLDER);
        }

        for (Consumer<TestType> listener : myTestTypeListeners) {
            listener.accept(testType);
        }
    }

    @RequiredUIAccess
    public void setPatternVisible(boolean visible) {
        myPatternIsVisible = visible;
        myPatternRow.setVisible(visible && getTestType() == TestType.TEST_FOLDER);
    }

    public Component getPanel() {
        return myRootPanel;
    }

    public VerticalLayout getAdditionalPanel() {
        return myAdditionalPanel;
    }

    public LabeledLayout getTestsPanel() {
        return myTestsPanel;
    }

    public TextBox getPatternComponent() {
        return myPatternTextField;
    }

    @Override
    public boolean usePattern() {
        return myPatternCheckBox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void usePattern(boolean usePattern) {
        myPatternCheckBox.setValue(usePattern);
        myPatternTextField.setEnabled(usePattern);
    }

    @RequiredUIAccess
    public String getParams() {
        return StringUtil.notNullize(myParamTextField.getValue()).trim();
    }

    public CheckBox getParamCheckBox() {
        return myParamCheckBox;
    }

    @RequiredUIAccess
    public void setParams(@Nullable String params) {
        myParamTextField.setValue(StringUtil.notNullize(params));
    }

    @RequiredUIAccess
    public void setParamsVisible() {
        myParamRow.setVisible(true);
    }

    private record Row(Component label, Component field) {
        @RequiredUIAccess
        void setVisible(boolean visible) {
            label.setVisible(visible);
            field.setVisible(visible);
        }
    }
}
