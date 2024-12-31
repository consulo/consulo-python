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
package com.jetbrains.python.jython.psi.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearchExecutor;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyJavaSuperMethodsSearchExecutor implements PySuperMethodsSearchExecutor
{
	public boolean execute(@Nonnull final PySuperMethodsSearch.SearchParameters queryParameters, @Nonnull final Processor<? super PsiElement> consumer)
	{
		PyFunction func = queryParameters.getDerivedMethod();
		PyClass containingClass = func.getContainingClass();
		if(containingClass != null)
		{
			for(PyClassLikeType type : containingClass.getSuperClassTypes(TypeEvalContext.codeInsightFallback(containingClass.getProject())))
			{
				if(type instanceof PyJavaClassType)
				{
					final PsiClass psiClass = ((PyJavaClassType) type).getPsiClass();
					PsiMethod[] methods = psiClass.findMethodsByName(func.getName(), true);
					// the Python method actually does override/implement all of Java super methods with the same name
					if(!ContainerUtil.process(methods, consumer))
					{
						return false;
					}
				}
			}
		}
		return true;
	}
}
