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
package com.jetbrains.python.impl.run;

import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.run.PythonRunConfigurationParams;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.TextComponentAccessor;
import consulo.ui.util.FormBuilder;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class PythonRunConfigurationForm implements PythonRunConfigurationParams {
    private final Project myProject;
    private final AbstractPyCommonOptionsForm myCommonOptionsForm;
    private final FileChooserTextBoxBuilder.Controller myScriptTextField;
    private final TextBoxWithExpandAction myScriptParametersTextField;
    private final CheckBox myShowCommandLineCheckbox;
    private final Component myRootPanel;

    @RequiredUIAccess
    public PythonRunConfigurationForm(PythonRunConfiguration configuration, Disposable uiDisposable) {
        myProject = configuration.getProject();
        myCommonOptionsForm = myProject.getApplication()
            .getInstance(PyCommonOptionsFormFactory.class)
            .createForm(configuration.getCommonOptionsFormData(), uiDisposable);

        FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                return file.isDirectory() || file.getExtension() == null || Comparing.equal(file.getExtension(), "py");
            }
        };

        myScriptTextField = FileChooserTextBoxBuilder.create(myProject)
            .uiDisposable(uiDisposable)
            .fileChooserDescriptor(chooserDescriptor)
            .dialogTitle(PyLocalize.runcfgLabelsSelectScript())
            .textBoxAccessor(new TextComponentAccessor<>() {
                @RequiredUIAccess
                @Override
                public String getValue(TextBox component) {
                    return StringUtil.notNullize(component.getValue());
                }

                @RequiredUIAccess
                @Override
                public void setValue(TextBox component, String text) {
                    setValue(component, text, true);
                    onScriptChosen(text);
                }

                @RequiredUIAccess
                @Override
                public void setValue(TextBox component, String text, boolean fireListeners) {
                    component.setValue(text, fireListeners);
                }
            })
            .build();

        myScriptParametersTextField = TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            PyLocalize.runcfgCaptionsScript_parameters_dialog().get(),
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );

        myShowCommandLineCheckbox = CheckBox.create(PyLocalize.runcfgLabelsShowCommandLine(), true);

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(PyLocalize.runcfgLabelsScript(), myScriptTextField.getComponent());
        builder.addLabeled(PyLocalize.runcfgLabelsScript_parameters(), myScriptParametersTextField);
        builder.addBottom(myCommonOptionsForm.getMainPanel());
        builder.addBottom(myShowCommandLineCheckbox);
        myRootPanel = builder.build();
    }

    @RequiredUIAccess
    private void onScriptChosen(String path) {
        String parentPath = PathUtil.getParentPath(FileUtil.toSystemIndependentName(path));
        if (!parentPath.isEmpty()) {
            myCommonOptionsForm.setWorkingDirectory(parentPath);
        }
    }

    public Component getPanel() {
        return myRootPanel;
    }

    @Override
    public AbstractPythonRunConfigurationParams getBaseParams() {
        return myCommonOptionsForm;
    }

    @RequiredUIAccess
    @Override
    public String getScriptName() {
        return FileUtil.toSystemIndependentName(myScriptTextField.getValue().trim());
    }

    @RequiredUIAccess
    @Override
    public void setScriptName(@Nullable String scriptName) {
        myScriptTextField.getComponent().setValue(scriptName == null ? "" : FileUtil.toSystemDependentName(scriptName));
    }

    @RequiredUIAccess
    @Override
    public String getScriptParameters() {
        return StringUtil.notNullize(myScriptParametersTextField.getValue()).trim();
    }

    @RequiredUIAccess
    @Override
    public void setScriptParameters(@Nullable String scriptParameters) {
        myScriptParametersTextField.setValue(StringUtil.notNullize(scriptParameters));
    }

    @Override
    public boolean showCommandLineAfterwards() {
        return myShowCommandLineCheckbox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void setShowCommandLineAfterwards(boolean showCommandLineAfterwards) {
        myShowCommandLineCheckbox.setValue(showCommandLineAfterwards);
    }

    public boolean isMultiprocessMode() {
        return myProject.getInstance(PyDebuggerOptionsProvider.class).isAttachToSubprocess();
    }

    public void setMultiprocessMode(boolean multiprocess) {
    }
}
