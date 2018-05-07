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
package com.jetbrains.python.testing.attest;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;

/**
 * User: catherine
 */
public class PythonAtTestConfigurationProducer extends PythonTestConfigurationProducer
{
	public PythonAtTestConfigurationProducer()
	{
		super(PythonTestConfigurationType.getInstance().PY_ATTEST_FACTORY);
	}

	protected boolean isAvailable(@Nonnull final Location location)
	{
		final PsiElement element = location.getPsiElement();
		Module module = location.getModule();
		if(module == null)
		{
			module = ModuleUtilCore.findModuleForPsiElement(element);
		}

		final Sdk sdk = PythonSdkType.findPythonSdk(module);
		return module != null && TestRunnerService.getInstance(module).getProjectConfiguration().equals(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME) && sdk != null;
	}

	@Override
	protected boolean isTestClass(@Nonnull final PyClass pyClass, @Nullable final AbstractPythonTestRunConfiguration configuration, @Nullable final TypeEvalContext context)
	{
		for(PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback(pyClass.getProject())))
		{
			if(type != null && "TestBase".equals(type.getName()) && hasTestFunction(pyClass))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasTestFunction(@Nonnull final PyClass pyClass)
	{
		PyFunction[] methods = pyClass.getMethods();
		for(PyFunction function : methods)
		{
			PyDecoratorList decorators = function.getDecoratorList();
			if(decorators == null)
			{
				continue;
			}
			for(PyDecorator decorator : decorators.getDecorators())
			{
				if("test".equals(decorator.getName()) || "test_if".equals(decorator.getName()))
				{
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isTestFunction(@Nonnull final PyFunction pyFunction, @Nullable final AbstractPythonTestRunConfiguration configuration)
	{
		PyDecoratorList decorators = pyFunction.getDecoratorList();
		if(decorators == null)
		{
			return false;
		}
		for(PyDecorator decorator : decorators.getDecorators())
		{
			if("test".equals(decorator.getName()) || "test_if".equals(decorator.getName()))
			{
				return true;
			}
		}
		return false;
	}

	protected List<PyStatement> getTestCaseClassesFromFile(@Nonnull final PyFile file)
	{
		List<PyStatement> result = Lists.newArrayList();
		for(PyClass cls : file.getTopLevelClasses())
		{
			if(isTestClass(cls, null, null))
			{
				result.add(cls);
			}
		}

		for(PyFunction cls : file.getTopLevelFunctions())
		{
			if(isTestFunction(cls, null))
			{
				result.add(cls);
			}
		}
		return result;
	}
}