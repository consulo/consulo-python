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

/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.impl.testing.unittest;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.testing.*;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.Location;
import consulo.language.psi.PsiElement;
import consulo.module.Module;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

@ExtensionImpl
public class PythonUnitTestConfigurationProducer extends PythonTestConfigurationProducer {
    public PythonUnitTestConfigurationProducer() {
        super(PythonTestConfigurationType.getInstance().PY_UNITTEST_FACTORY);
    }

    @Override
    @RequiredReadAction
    protected boolean isAvailable(@Nonnull Location location) {
        PsiElement element = location.getPsiElement();
        Module module = element.getModule();
        return module != null && TestRunnerService.getInstance(module)
            .getProjectConfiguration()
            .equals(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
    }

    @Override
    protected boolean isTestFunction(
        @Nonnull PyFunction pyFunction,
        @Nullable AbstractPythonTestRunConfiguration configuration
    ) {
        boolean isTestFunction = super.isTestFunction(pyFunction, configuration);
        return isTestFunction
            || (configuration instanceof PythonUnitTestRunConfiguration unitTestRunConfig && !unitTestRunConfig.isPureUnittest());
    }

    @Override
    protected boolean isTestClass(
        @Nonnull PyClass pyClass,
        @Nullable AbstractPythonTestRunConfiguration configuration,
        TypeEvalContext context
    ) {
        boolean isTestClass = super.isTestClass(pyClass, configuration, context);
        return isTestClass
            || (configuration instanceof PythonUnitTestRunConfiguration unitTestRunConfig && !unitTestRunConfig.isPureUnittest());
    }

    @Override
    @RequiredReadAction
    protected boolean isTestFile(@Nonnull PyFile file) {
        if (PyNames.SETUP_DOT_PY.equals(file.getName())) {
            return true;
        }
        List<PyStatement> testCases = getTestCaseClassesFromFile(file);
        return !testCases.isEmpty();
    }
}