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

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.localize.LocalizeValue;
import consulo.module.ui.BundleBox;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ComboBox;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;

/**
 * Whether the configuration runs on the SDK of its module or on an explicitly chosen interpreter.
 */
public class PythonInterpreterFragment<T extends AbstractPythonRunConfiguration> implements RunConfigurationFragment<T> {
    @Override
    public String getId() {
        return "pythonInterpreter";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.runConfigurationFragmentInterpreter();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.MODULE + 10;
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<T> createEditor(Project project, Disposable parent) {
        BundleBox interpreter = BundleBox.builder(parent)
            .withSdkTypeFilterByClass(PythonSdkType.class)
            .withNoneItem()
            .build();

        RadioGroup<Boolean> useModuleSdk = RadioGroup.create();
        VerticalLayout panel = VerticalLayout.create(Space.NONE);
        panel.add(useModuleSdk.newButton(PyLocalize.runcfgLabelsUseModuleSdk(), Boolean.TRUE));
        panel.add(DockLayout.create(Space.SMALL)
            .left(useModuleSdk.newButton(PyLocalize.runcfgLabelsInterpreter(), Boolean.FALSE))
            .center(interpreter.getComponent()));
        useModuleSdk.addValueListener(value -> interpreter.getComponent().setEnabled(!Boolean.TRUE.equals(value)));

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(PyLocalize.runcfgLabelsPythonInterpreter(), panel);
            }

            @RequiredUIAccess
            @Override
            public void reset(T target) {
                useModuleSdk.setValue(target.isUseModuleSdk(), false);
                interpreter.getComponent().setEnabled(!target.isUseModuleSdk());

                String sdkHome = target.getOptions().getSdkHome();
                if (StringUtil.isEmptyOrSpaces(sdkHome)) {
                    interpreter.setSelectedNoneBundle();
                    return;
                }
                ComboBox<BundleBox.BundleBoxItem> comboBox = interpreter.getComponent();
                for (BundleBox.BundleBoxItem item : comboBox.getDataModel()) {
                    Sdk sdk = item.getBundle();
                    if (sdk != null && FileUtil.pathsEqual(sdkHome, sdk.getHomePath())) {
                        comboBox.setValue(item);
                        return;
                    }
                }
                interpreter.setInvalidBundle(sdkHome);
            }

            @RequiredUIAccess
            @Override
            public void apply(T target) {
                target.setUseModuleSdk(Boolean.TRUE.equals(useModuleSdk.getValue()));

                BundleBox.BundleBoxItem item = interpreter.getComponent().getValue();
                Sdk sdk = item == null ? null : item.getBundle();
                target.setSdkHome(item == null ? null : sdk != null ? sdk.getHomePath() : item.getBundleName());
            }
        };
    }
}
