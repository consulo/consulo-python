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

import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration.TestType;
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
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.RadioGroup;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;

import java.util.List;

/**
 * What a Python test configuration runs: a folder, a script, a class, a method or a function.
 */
public class PythonTestTargetFragment<T extends AbstractPythonTestRunConfiguration> implements RunConfigurationFragment<T> {
    private final boolean myPatternSupported;

    public PythonTestTargetFragment(boolean patternSupported) {
        myPatternSupported = patternSupported;
    }

    @Override
    public String getId() {
        return "pythonTestTarget";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentTestTarget();
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
    public RunConfigurationFragmentEditor<T> createEditor(Project project, Disposable parent) {
        RadioGroup<TestType> testType = RadioGroup.create();
        Component testTypes = testType.fillHorizontal(List.of(TestType.values()), PythonTestTargetFragment::getTestTypeName);

        FileChooserTextBoxBuilder.Controller folder = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(parent)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .dialogTitle(PyLocalize.runcfgUnittestDlgSelectFolderPath())
            .build();
        CheckBox usePattern = CheckBox.create(PyLocalize.runcfgUnittestDlgPattern());
        TextBox pattern = TextBox.create();
        pattern.setToolTipText(PyLocalize.runcfgUnittestDlgPatternTooltip());
        FileChooserTextBoxBuilder.Controller script = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(parent)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(PyLocalize.runcfgUnittestDlgSelectScriptPath())
            .build();
        TextBox className = TextBox.create();
        Label methodLabel = Label.create(PyLocalize.runcfgUnittestDlgMethod_label());
        TextBox methodName = TextBox.create();

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(PyLocalize.runcfgUnittestDlgTest_type_title(), testTypes);
        Row folderRow = Row.add(builder, Label.create(PyLocalize.runcfgUnittestDlgFolder_path()), folder.getComponent());
        Row patternRow = Row.add(builder, usePattern, pattern);
        Row scriptRow = Row.add(builder, Label.create(PyLocalize.runcfgUnittestDlgTest_script_label()), script.getComponent());
        Row classRow = Row.add(builder, Label.create(PyLocalize.runcfgUnittestDlgClass_label()), className);
        Row methodRow = Row.add(builder, methodLabel, methodName);
        Component form = builder.build();

        Runnable update = () -> {
            TestType type = testType.getValue() == null ? TestType.TEST_SCRIPT : testType.getValue();
            folderRow.setVisible(type == TestType.TEST_FOLDER);
            patternRow.setVisible(myPatternSupported && type == TestType.TEST_FOLDER);
            scriptRow.setVisible(type != TestType.TEST_FOLDER);
            classRow.setVisible(type == TestType.TEST_CLASS || type == TestType.TEST_METHOD);
            methodRow.setVisible(type == TestType.TEST_METHOD || type == TestType.TEST_FUNCTION);
            methodLabel.setText(type == TestType.TEST_METHOD
                ? PyLocalize.runcfgUnittestDlgMethod_label()
                : PyLocalize.runcfgUnittestDlgFunction_label());
            pattern.setEnabled(usePattern.getValueOrError());
        };
        testType.addValueListener(value -> update.run());
        usePattern.addValueListener(event -> pattern.setEnabled(Boolean.TRUE.equals(event.getValue())));

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addBottom(form);
            }

            @RequiredUIAccess
            @Override
            public void reset(T target) {
                testType.setValue(target.getTestType(), false);
                folder.setValue(FileUtil.toSystemDependentName(target.getFolderName()), false);
                usePattern.setValue(target.usePattern(), false);
                pattern.setValue(target.getPattern(), false);
                script.setValue(FileUtil.toSystemDependentName(target.getScriptName()), false);
                className.setValue(target.getClassName(), false);
                methodName.setValue(target.getMethodName(), false);
                update.run();
            }

            @RequiredUIAccess
            @Override
            public void apply(T target) {
                target.setTestType(testType.getValue() == null ? TestType.TEST_SCRIPT : testType.getValue());
                target.setFolderName(FileUtil.toSystemIndependentName(StringUtil.notNullize(folder.getValue()).trim()));
                target.usePattern(usePattern.getValueOrError());
                target.setPattern(StringUtil.notNullize(pattern.getValue()).trim());
                target.setScriptName(FileUtil.toSystemIndependentName(StringUtil.notNullize(script.getValue()).trim()));
                target.setClassName(StringUtil.notNullize(className.getValue()).trim());
                target.setMethodName(StringUtil.notNullize(methodName.getValue()).trim());
            }
        };
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

    private record Row(Component label, Component field) {
        @RequiredUIAccess
        static Row add(FormBuilder builder, Component label, Component field) {
            builder.addLabeled(label, field);
            return new Row(label, field);
        }

        @RequiredUIAccess
        void setVisible(boolean visible) {
            label.setVisible(visible);
            field.setVisible(visible);
        }
    }
}
