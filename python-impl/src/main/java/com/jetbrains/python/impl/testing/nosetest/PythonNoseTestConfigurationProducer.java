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

package com.jetbrains.python.impl.testing.nosetest;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.testing.*;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.execution.action.Location;
import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.module.ModuleManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class PythonNoseTestConfigurationProducer extends
		PythonTestConfigurationProducer
{
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