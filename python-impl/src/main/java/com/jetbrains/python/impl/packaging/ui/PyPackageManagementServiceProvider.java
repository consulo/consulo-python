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
package com.jetbrains.python.impl.packaging.ui;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.packaging.PyPackageManagers;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.repository.ui.PackageManagementService;
import consulo.repository.ui.PackageManagementServiceProvider;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * The packages of a Python interpreter - the target is the name of its SDK.
 */
@ExtensionImpl
public class PyPackageManagementServiceProvider implements PackageManagementServiceProvider {
    @Override
    public String getId() {
        return "python";
    }

    @Override
    public LocalizeValue getName() {
        return LocalizeValue.localizeTODO("Python Packages");
    }

    @Override
    public Image getIcon() {
        return PythonPsiIconGroup.python();
    }

    @Override
    public @Nullable PackageManagementService create(Project project, String target) {
        for (Sdk sdk : PythonSdkType.getAllSdks()) {
            if (target.equals(sdk.getName())) {
                return PyPackageManagers.getInstance().getManagementService(project, sdk);
            }
        }
        return null;
    }
}
