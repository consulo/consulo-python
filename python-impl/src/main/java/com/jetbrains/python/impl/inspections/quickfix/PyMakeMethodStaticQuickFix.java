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
package com.jetbrains.python.impl.inspections.quickfix;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFix implements LocalQuickFix
{
	public PyMakeMethodStaticQuickFix()
	{
	}

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.make.static");
	}

	public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
		if(problemFunction == null)
		{
			return;
		}
		final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);

		final PyParameter[] parameters = problemFunction.getParameterList().getParameters();
		if(parameters.length > 0)
		{
			parameters[0].delete();
		}
		final PyDecoratorList problemDecoratorList = problemFunction.getDecoratorList();
		List<String> decoTexts = new ArrayList<>();
		decoTexts.add("@staticmethod");
		if(problemDecoratorList != null)
		{
			final PyDecorator[] decorators = problemDecoratorList.getDecorators();
			for(PyDecorator deco : decorators)
			{
				decoTexts.add(deco.getText());
			}
		}

		PyElementGenerator generator = PyElementGenerator.getInstance(project);
		final PyDecoratorList decoratorList = generator.createDecoratorList(decoTexts.toArray(new String[decoTexts.size()]));

		if(problemDecoratorList != null)
		{
			problemDecoratorList.replace(decoratorList);
		}
		else
		{
			problemFunction.addBefore(decoratorList, problemFunction.getFirstChild());
		}

		for(UsageInfo usage : usages)
		{
			final PsiElement usageElement = usage.getElement();
			if(usageElement instanceof PyReferenceExpression)
			{
				updateUsage((PyReferenceExpression) usageElement);
			}
		}
	}

	private static void updateUsage(@Nonnull final PyReferenceExpression element)
	{
		final PyExpression qualifier = element.getQualifier();
		if(qualifier == null)
		{
			return;
		}
		final PsiReference reference = qualifier.getReference();
		if(reference == null)
		{
			return;
		}
		final PsiElement resolved = reference.resolve();
		if(resolved instanceof PyClass)
		{     //call with first instance argument A.m(A())
			updateArgumentList(element);
		}
	}

	private static void updateArgumentList(@Nonnull final PyReferenceExpression element)
	{
		final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
		if(callExpression == null)
		{
			return;
		}
		final PyArgumentList argumentList = callExpression.getArgumentList();
		if(argumentList == null)
		{
			return;
		}
		final PyExpression[] arguments = argumentList.getArguments();
		if(arguments.length > 0)
		{
			arguments[0].delete();
		}
	}
}
