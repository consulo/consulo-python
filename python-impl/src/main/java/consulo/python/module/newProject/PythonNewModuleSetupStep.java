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
package consulo.python.module.newProject;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.SdkTable;
import consulo.disposer.Disposable;
import consulo.module.creation.ui.UnifiedProjectOrModuleNameStep;
import consulo.module.ui.BundleBox;
import consulo.module.ui.BundleBoxBuilder;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ComboBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The name and the interpreter of a new Python module - an interpreter of the machine the module is created on.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public class PythonNewModuleSetupStep extends UnifiedProjectOrModuleNameStep<PythonNewModuleContext> {
    private @Nullable BundleBox myBundleBox;

    public PythonNewModuleSetupStep(PythonNewModuleContext context) {
        super(context);
    }

    @RequiredUIAccess
    @Override
    protected void extend(FormBuilder builder, Disposable uiDisposable) {
        super.extend(builder, uiDisposable);

        BundleBoxBuilder boxBuilder = BundleBoxBuilder.create(uiDisposable);
        boxBuilder.withSdkTypeFilterByType(PythonSdkType.getInstance());

        BundleBox bundleBox = boxBuilder.build();
        myBundleBox = bundleBox;
        builder.addLabeled(PyLocalize.runcfgLabelsPythonInterpreter(), bundleBox.getComponent());

        ComboBox<BundleBox.BundleBoxItem> component = bundleBox.getComponent();
        if (component.getDataModel().getSize() > 0) {
            component.setValueByIndex(0);
        }
        // the interpreters of the target machine
        bindBundleBox(bundleBox);
    }

    @Override
    public void onStepLeave(PythonNewModuleContext context) {
        super.onStepLeave(context);

        BundleBox bundleBox = myBundleBox;
        String selectedBundleName = bundleBox == null ? null : bundleBox.getSelectedBundleName();
        if (selectedBundleName != null) {
            context.setSdk(SdkTable.getInstance().findSdk(selectedBundleName));
        }
    }
}
