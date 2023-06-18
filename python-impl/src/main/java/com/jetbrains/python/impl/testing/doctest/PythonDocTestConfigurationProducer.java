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
 * User: catherine
 */
package com.jetbrains.python.impl.testing.doctest;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.execution.action.Location;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.impl.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.impl.testing.PythonTestConfigurationType;

public class PythonDocTestConfigurationProducer extends PythonTestConfigurationProducer
{

	public PythonDocTestConfigurationProducer()
	{
		super(PythonTestConfigurationType.getInstance().PY_DOCTEST_FACTORY);
	}

	@Override
	protected boolean isTestFunction(@Nonnull final PyFunction pyFunction, @Nullable final AbstractPythonTestRunConfiguration configuration)
	{
		return PythonDocTestUtil.isDocTestFunction(pyFunction);
	}

	@Override
	protected boolean isTestClass(@Nonnull PyClass pyClass, @Nullable final AbstractPythonTestRunConfiguration configuration, @Nullable final TypeEvalContext context)
	{
		return PythonDocTestUtil.isDocTestClass(pyClass);
	}

	@Override
	protected boolean isTestFile(@Nonnull PyFile file)
	{
		final List<PyElement> testCases = PythonDocTestUtil.getDocTestCasesFromFile(file);
		return !testCases.isEmpty();
	}

	protected boolean isAvailable(@Nonnull final Location location)
	{
		final Module module = location.getModule();
		if(!isPythonModule(module))
		{
			return false;
		}
		final PsiElement element = location.getPsiElement();
		if(element instanceof PsiFile)
		{
			final PyDocTestVisitor visitor = new PyDocTestVisitor();
			element.accept(visitor);
			return visitor.hasTests;
		}
		else
		{
			return true;
		}
	}

	private static class PyDocTestVisitor extends PsiRecursiveElementVisitor
	{
		boolean hasTests = false;

		@Override
		public void visitFile(PsiFile node)
		{
			if(node instanceof PyFile)
			{
				List<PyElement> testClasses = PythonDocTestUtil.getDocTestCasesFromFile((PyFile) node);
				if(!testClasses.isEmpty())
				{
					hasTests = true;
				}
			}
			else
			{
				final String text = node.getText();
				if(PythonDocTestUtil.hasExample(text))
				{
					hasTests = true;
				}
			}
		}
	}

	@Override
	protected boolean isTestFolder(@Nonnull VirtualFile virtualFile, @Nonnull Project project)
	{
		return false;
	}

}