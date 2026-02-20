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
package com.jetbrains.python.packaging;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.content.bundle.Sdk;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.repository.ui.PackageManagementService;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class PyPackageManagers {

  @Nonnull
  public static PyPackageManagers getInstance() {
    return ServiceManager.getService(PyPackageManagers.class);
  }

  @Nonnull
  public abstract PyPackageManager forSdk(@Nonnull Sdk sdk);

  public abstract PackageManagementService getManagementService(Project project, Sdk sdk);

  public abstract void clearCache(@Nonnull Sdk sdk);
}
