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

package com.jetbrains.python.testing.nosetest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.*;

public class PythonNoseTestConfigurationProducer extends
                                                 PythonTestConfigurationProducer {
  public PythonNoseTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_NOSETEST_FACTORY);
  }

  protected boolean isAvailable(@Nonnull final Location location) {
    final PsiElement element = location.getPsiElement();
    Module module = location.getModule();
    if (module == null) {
      final Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
      if (modules.length == 0) return false;
      module = modules[0];
    }
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    return (TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME) && sdk != null);
  }

  @Override
  protected boolean isTestFunction(@Nonnull final PyFunction pyFunction, @Nullable final AbstractPythonTestRunConfiguration configuration) {
    return PythonUnitTestUtil.isTestCaseFunction(pyFunction, false);
  }
}