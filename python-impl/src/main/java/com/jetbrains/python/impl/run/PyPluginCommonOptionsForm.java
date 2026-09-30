/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.ApplicationPropertiesComponent;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.EnvironmentVariablesTextFieldWithBrowseButton;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.ide.impl.idea.execution.util.PathMappingsComponent;
import consulo.ide.impl.idea.util.PathMappingSettings;
import consulo.module.Module;
import consulo.module.ModulesAlphaComparator;
import consulo.module.ui.BundleBox;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.RadioGroup;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.FoldoutLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.util.FormBuilder;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm {
    private final MutableFlatDataModel<Module> myModules;
    private final Map<Module, Sdk> myModuleSdks = new HashMap<>();
    private final EnvironmentVariablesTextFieldWithBrowseButton myEnvsComponent;
    private final RadioGroup<Boolean> myUseModuleSdkGroup;
    private final ComboBox<Module> myModuleComboBox;
    private final BundleBox myInterpreterComboBox;
    private final TextBoxWithExpandAction myInterpreterOptionsTextField;
    private final FileChooserTextBoxBuilder.Controller myWorkingDirectoryTextField;
    private final CheckBox myAddContentRootsCheckbox;
    private final CheckBox myAddSourceRootsCheckbox;
    private final @Nullable PathMappingsComponent myPathMappingsComponent;
    private final @Nullable Component myPathMappingsPanel;
    private final FoldoutLayout myMainPanel;

    private PathMappingSettings myMappingSettings = new PathMappingSettings();

    @RequiredUIAccess
    public PyPluginCommonOptionsForm(PyCommonOptionsFormData data, Disposable uiDisposable) {
        Project project = data.getProject();

        List<Module> validModules = new ArrayList<>(data.getValidModules());
        validModules.sort(new ModulesAlphaComparator());
        myModules = FlatDataModel.of(validModules);

        myModuleComboBox = ComboBox.create(myModules);
        myModuleComboBox.setRender((presentation, item) -> {
            Module module = item.getValue();
            if (module != null) {
                presentation.withIcon(PlatformIconGroup.nodesModule());
                presentation.append(module.getName());
            }
        });
        if (!validModules.isEmpty()) {
            myModuleComboBox.setValue(validModules.get(0));
        }

        myInterpreterComboBox = BundleBox.builder(uiDisposable)
            .withSdkTypeFilterByClass(PythonSdkType.class)
            .withNoneItem()
            .build();

        myEnvsComponent = new EnvironmentVariablesTextFieldWithBrowseButton();

        myInterpreterOptionsTextField = TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            PyLocalize.runcfgCaptionsInterpreter_options_dialog().get(),
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );

        myWorkingDirectoryTextField = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(uiDisposable)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .dialogTitle(ExecutionLocalize.selectWorkingDirectoryMessage())
            .build();

        myAddContentRootsCheckbox = CheckBox.create(PyLocalize.runcfgLabelsAddContentRoots(), true);
        myAddSourceRootsCheckbox = CheckBox.create(PyLocalize.runcfgLabelsAddSourceRoots(), true);

        myUseModuleSdkGroup = RadioGroup.create();
        FormBuilder interpreterForm = FormBuilder.create();
        interpreterForm.addLabeled(myUseModuleSdkGroup.newButton(PyLocalize.runcfgLabelsUseModuleSdk(), Boolean.TRUE), myModuleComboBox);
        interpreterForm.addLabeled(
            myUseModuleSdkGroup.newButton(PyLocalize.runcfgLabelsInterpreter(), Boolean.FALSE),
            myInterpreterComboBox.getComponent()
        );
        myUseModuleSdkGroup.setValue(Boolean.TRUE, false);

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(PyLocalize.runcfgLabelsEnvironment_variables(), myEnvsComponent.getComponent());
        builder.addLabeled(PyLocalize.runcfgLabelsPythonInterpreter(), interpreterForm.build());
        builder.addLabeled(PyLocalize.runcfgLabelsInterpreter_options(), myInterpreterOptionsTextField);
        builder.addLabeled(PyLocalize.runcfgLabelsWorking_directory(), myWorkingDirectoryTextField.getComponent());

        if (project.getApplication().isUnifiedApplication()) {
            myPathMappingsComponent = null;
            myPathMappingsPanel = null;
        }
        else {
            for (Module module : validModules) {
                Sdk sdk = PythonSdkType.findPythonSdk(module);
                if (sdk != null) {
                    myModuleSdks.put(module, sdk);
                }
            }

            PathMappingsComponent pathMappingsComponent = new PathMappingsComponent();
            Component pathMappingsPanel = TargetAWT.wrap(pathMappingsComponent);
            builder.addBottom(pathMappingsPanel);
            myPathMappingsComponent = pathMappingsComponent;
            myPathMappingsPanel = pathMappingsPanel;
        }

        builder.addBottom(myAddContentRootsCheckbox);
        builder.addBottom(myAddSourceRootsCheckbox);

        ApplicationPropertiesComponent properties = project.getApplication().getInstance(ApplicationPropertiesComponent.class);
        myMainPanel = FoldoutLayout.create(
            PyLocalize.runcfgLabelsEnvironment(),
            builder.build(),
            properties.getBoolean(EXPAND_PROPERTY_KEY, true)
        );
        myMainPanel.addOpenedListener(event -> properties.setValue(EXPAND_PROPERTY_KEY, event.isOpened(), true));

        myUseModuleSdkGroup.addValueListener(value -> updateControls());
        myModuleComboBox.addValueListener(event -> updateControls());
        myInterpreterComboBox.getComponent().addValueListener(event -> updateControls());

        updateControls();
    }

    @RequiredUIAccess
    private void updateControls() {
        boolean useModuleSdk = isUseModuleSdk();
        myModuleComboBox.setEnabled(useModuleSdk);
        myInterpreterComboBox.getComponent().setEnabled(!useModuleSdk);

        Component pathMappingsPanel = myPathMappingsPanel;
        if (pathMappingsPanel != null) {
            pathMappingsPanel.setVisible(PySdkUtil.isRemote(getSelectedSdk()));
        }
    }

    private @Nullable Sdk getSelectedSdk() {
        if (isUseModuleSdk()) {
            Module module = getModule();
            return module == null ? null : myModuleSdks.get(module);
        }
        BundleBox.BundleBoxItem item = myInterpreterComboBox.getComponent().getValue();
        return item == null ? null : item.getBundle();
    }

    @RequiredUIAccess
    @Override
    public Component getMainPanel() {
        return myMainPanel;
    }

    @Override
    public void subscribe() {
    }

    @Override
    public Disposable addInterpreterListener(Runnable listener) {
        return myInterpreterComboBox.getComponent().addValueListener(event -> listener.run());
    }

    @RequiredUIAccess
    @Override
    public String getInterpreterOptions() {
        return StringUtil.notNullize(myInterpreterOptionsTextField.getValue()).trim();
    }

    @RequiredUIAccess
    @Override
    public void setInterpreterOptions(@Nullable String interpreterOptions) {
        myInterpreterOptionsTextField.setValue(StringUtil.notNullize(interpreterOptions));
    }

    @RequiredUIAccess
    @Override
    public String getWorkingDirectory() {
        return FileUtil.toSystemIndependentName(myWorkingDirectoryTextField.getValue().trim());
    }

    @RequiredUIAccess
    @Override
    public void setWorkingDirectory(@Nullable String workingDirectory) {
        myWorkingDirectoryTextField.setValue(workingDirectory == null ? "" : FileUtil.toSystemDependentName(workingDirectory));
    }

    @Override
    public @Nullable String getSdkHome() {
        BundleBox.BundleBoxItem item = myInterpreterComboBox.getComponent().getValue();
        if (item == null) {
            return null;
        }
        Sdk sdk = item.getBundle();
        return sdk != null ? sdk.getHomePath() : item.getBundleName();
    }

    @RequiredUIAccess
    @Override
    public void setSdkHome(@Nullable String sdkHome) {
        if (StringUtil.isEmptyOrSpaces(sdkHome)) {
            myInterpreterComboBox.setSelectedNoneBundle();
            return;
        }

        ComboBox<BundleBox.BundleBoxItem> comboBox = myInterpreterComboBox.getComponent();
        for (BundleBox.BundleBoxItem item : comboBox.getDataModel()) {
            Sdk sdk = item.getBundle();
            if (sdk != null && FileUtil.pathsEqual(sdkHome, sdk.getHomePath())) {
                comboBox.setValue(item);
                return;
            }
        }
        myInterpreterComboBox.setInvalidBundle(sdkHome);
    }

    @Override
    public @Nullable Module getModule() {
        return myModuleComboBox.getValue();
    }

    @Override
    public @Nullable String getModuleName() {
        Module module = getModule();
        return module != null ? module.getName() : null;
    }

    @RequiredUIAccess
    @Override
    public void setModule(@Nullable Module module) {
        if (module != null && myModules.indexOf(module) < 0) {
            myModules.add(module);
        }
        myModuleComboBox.setValue(module);
    }

    @Override
    public boolean isUseModuleSdk() {
        return Boolean.TRUE.equals(myUseModuleSdkGroup.getValue());
    }

    @RequiredUIAccess
    @Override
    public void setUseModuleSdk(boolean useModuleSdk) {
        myUseModuleSdkGroup.setValue(useModuleSdk, false);
        updateControls();
    }

    @Override
    public boolean isPassParentEnvs() {
        return myEnvsComponent.isPassParentEnvs();
    }

    @RequiredUIAccess
    @Override
    public void setPassParentEnvs(boolean passParentEnvs) {
        myEnvsComponent.setPassParentEnvs(passParentEnvs);
    }

    @Override
    public Map<String, String> getEnvs() {
        return new LinkedHashMap<>(myEnvsComponent.getEnvs());
    }

    @RequiredUIAccess
    @Override
    public void setEnvs(Map<String, String> envs) {
        myEnvsComponent.setEnvs(envs);
    }

    @Override
    public PathMappingSettings getMappingSettings() {
        PathMappingsComponent pathMappingsComponent = myPathMappingsComponent;
        return pathMappingsComponent != null ? pathMappingsComponent.getMappingSettings() : myMappingSettings;
    }

    @Override
    public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
        PathMappingsComponent pathMappingsComponent = myPathMappingsComponent;
        if (pathMappingsComponent != null) {
            pathMappingsComponent.setMappingSettings(mappingSettings);
        }
        else {
            myMappingSettings = mappingSettings == null ? new PathMappingSettings() : mappingSettings;
        }
    }

    @Override
    public boolean shouldAddContentRoots() {
        return myAddContentRootsCheckbox.getValueOrError();
    }

    @Override
    public boolean shouldAddSourceRoots() {
        return myAddSourceRootsCheckbox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public void setAddContentRoots(boolean flag) {
        myAddContentRootsCheckbox.setValue(flag);
    }

    @RequiredUIAccess
    @Override
    public void setAddSourceRoots(boolean flag) {
        myAddSourceRootsCheckbox.setValue(flag);
    }
}
