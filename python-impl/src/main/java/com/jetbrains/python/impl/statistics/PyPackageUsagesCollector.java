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
package com.jetbrains.python.impl.statistics;

import com.jetbrains.python.impl.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.content.bundle.Sdk;
import consulo.externalService.statistic.AbstractApplicationUsagesCollector;
import consulo.externalService.statistic.CollectUsagesException;
import consulo.externalService.statistic.UsageDescriptor;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class PyPackageUsagesCollector extends AbstractApplicationUsagesCollector {
  @Nonnull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@Nonnull Project project) throws CollectUsagesException {
    Set<UsageDescriptor> result = new HashSet<>();
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      Sdk pythonSdk = PythonSdkType.findPythonSdk(m);
      if (pythonSdk != null) {
        ApplicationManager.getApplication().runReadAction(() -> {
          List<PyRequirement> requirements = PyPackageManager.getInstance(pythonSdk).getRequirements(m);
          if (requirements != null) {
            Collection<String> packages = new HashSet<>(PyPIPackageUtil.INSTANCE.getPackageNames());
            for (PyRequirement requirement : requirements) {
              String name = requirement.getName();
              if (packages.contains(name)) {
                result.add(new UsageDescriptor(name, 1));
              }
            }
          }
        });
      }
    }
    return result;
  }

  @Nonnull
  @Override
  public String getGroupId() {
    return "consulo.python.packages";
  }
}
