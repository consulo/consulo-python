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

package com.jetbrains.python.statistics;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PyInterpreterUsagesCollector extends AbstractApplicationUsagesCollector {
  @Nonnull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@Nonnull Project project) throws CollectUsagesException {
    Set<UsageDescriptor> result = new HashSet<UsageDescriptor>();
    for(Module m: ModuleManager.getInstance(project).getModules()) {
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
