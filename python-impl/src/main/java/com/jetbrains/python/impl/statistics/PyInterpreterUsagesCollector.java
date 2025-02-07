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

package com.jetbrains.python.impl.statistics;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.externalService.statistic.AbstractApplicationUsagesCollector;
import consulo.externalService.statistic.CollectUsagesException;
import consulo.externalService.statistic.UsageDescriptor;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class PyInterpreterUsagesCollector extends AbstractApplicationUsagesCollector {
  @Nonnull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@Nonnull Project project) throws CollectUsagesException {
    Set<UsageDescriptor> result = new HashSet<UsageDescriptor>();
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      Sdk pythonSdk = PythonSdkType.findPythonSdk(m);
      if (pythonSdk != null) {
        String versionString = pythonSdk.getVersionString();
        if (versionString != null) {
          result.add(new UsageDescriptor(versionString, 1));
        }
      }
    }
    return result;
  }

  @Nonnull
  @Override
  public String getGroupId() {
    return "consulo.python:interpreter";
  }
}
