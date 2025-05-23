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

import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;

public class PyRemoveParameterQuickFix implements LocalQuickFix
{

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.remove.parameter");
	}

	public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		assert element instanceof PyParameter;

		final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);

		if(pyFunction != null)
		{
			final List<UsageInfo> usages = PyRefactoringUtil.findUsages(pyFunction, false);
			for(UsageInfo usage : usages)
			{
				final PsiElement usageElement = usage.getElement();
				if(usageElement != null)
				{
					final PsiElement callExpression = usageElement.getParent();
					if(callExpression instanceof PyCallExpression)
					{
						final PyArgumentList argumentList = ((PyCallExpression) callExpression).getArgumentList();
						if(argumentList != null)
						{
							final PyResolveContext resolveContext = PyResolveContext.noImplicits();
							final PyCallExpression.PyArgumentsMapping mapping = ((PyCallExpression) callExpression).mapArguments(resolveContext);
							for(Map.Entry<PyExpression, PyNamedParameter> parameterEntry : mapping.getMappedParameters().entrySet())
							{
								if(parameterEntry.getValue().equals(element))
								{
									parameterEntry.getKey().delete();
								}
							}
						}
					}
				}
			}
			final PyStringLiteralExpression expression = pyFunction.getDocStringExpression();
			final String paramName = ((PyParameter) element).getName();
			if(expression != null && paramName != null)
			{
				PyDocstringGenerator.forDocStringOwner(pyFunction).withoutParam(paramName).buildAndInsert();
			}
		}
		element.delete();
	}
}
