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

package com.jetbrains.python.psi.impl;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import javax.annotation.Nonnull;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import consulo.codeInsight.completion.CompletionProvider;

/**
 * @author yole
 */
public class PyConstructorArgumentCompletionContributor extends CompletionContributor
{
	public PyConstructorArgumentCompletionContributor()
	{
		extend(CompletionType.BASIC, psiElement().withParents(PyReferenceExpression.class, PyArgumentList.class, PyCallExpression.class), new CompletionProvider()
		{
			@Override
			public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result)
			{
				final PyCallExpression call = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), PyCallExpression.class);
				if(call == null)
				{
					return;
				}
				final PyExpression calleeExpression = call.getCallee();
				if(calleeExpression instanceof PyReferenceExpression)
				{
					final PsiElement callee = ((PyReferenceExpression) calleeExpression).getReference().resolve();
					if(callee instanceof PsiClass)
					{
						addSettersAndListeners(result, (PsiClass) callee);
					}
					else if(callee instanceof PsiMethod && ((PsiMethod) callee).isConstructor())
					{
						final PsiClass containingClass = ((PsiMethod) callee).getContainingClass();
						assert containingClass != null;
						addSettersAndListeners(result, containingClass);
					}
				}
			}
		});
	}

	private static void addSettersAndListeners(CompletionResultSet result, PsiClass containingClass)
	{
		// see PyJavaType.init() in Jython source code for matching logic
		for(PsiMethod method : containingClass.getAllMethods())
		{
			if(PropertyUtil.isSimplePropertySetter(method))
			{
				final String propName = PropertyUtil.getPropertyName(method);
				result.addElement(PyUtil.createNamedParameterLookup(propName, containingClass.getProject()));
			}
			else if(method.getName().startsWith("add") && method.getName().endsWith("Listener") && PsiType.VOID.equals(method.getReturnType()))
			{
				final PsiParameter[] parameters = method.getParameterList().getParameters();
				if(parameters.length == 1)
				{
					final PsiType type = parameters[0].getType();
					if(type instanceof PsiClassType)
					{
						final PsiClass parameterClass = ((PsiClassType) type).resolve();
						if(parameterClass != null)
						{
							result.addElement(PyUtil.createNamedParameterLookup(StringUtil.decapitalize(parameterClass.getName()), containingClass.getProject()));
							for(PsiMethod parameterMethod : parameterClass.getMethods())
							{
								result.addElement(PyUtil.createNamedParameterLookup(parameterMethod.getName(), containingClass.getProject()));
							}
						}
					}
				}
			}
		}
	}
}
