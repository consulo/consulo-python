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
package com.jetbrains.python.psi.impl;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyJavaTypeProvider extends PyTypeProviderBase
{
	@Nullable
	public PyType getReferenceType(@Nonnull final PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor)
	{
		if(referenceTarget instanceof PsiClass)
		{
			return new PyJavaClassType((PsiClass) referenceTarget, true);
		}
		if(referenceTarget instanceof PsiJavaPackage)
		{
			return new PyJavaPackageType((PsiJavaPackage) referenceTarget, anchor == null ? null : ModuleUtil.findModuleForPsiElement(anchor));
		}
		if(referenceTarget instanceof PsiMethod)
		{
			PsiMethod method = (PsiMethod) referenceTarget;
			return new PyJavaMethodType(method);
		}
		if(referenceTarget instanceof PsiField)
		{
			return asPyType(((PsiField) referenceTarget).getType());
		}
		return null;
	}

	@Nullable
	public static PyType asPyType(PsiType type)
	{
		if(type instanceof PsiClassType)
		{
			final PsiClassType classType = (PsiClassType) type;
			final PsiClass psiClass = classType.resolve();
			if(psiClass != null)
			{
				return new PyJavaClassType(psiClass, false);
			}
		}
		return null;
	}

	public Ref<PyType> getParameterType(@Nonnull final PyNamedParameter param, @Nonnull final PyFunction func, @Nonnull TypeEvalContext context)
	{
		if(!(param.getParent() instanceof PyParameterList))
		{
			return null;
		}
		List<PyNamedParameter> params = ParamHelper.collectNamedParameters((PyParameterList) param.getParent());
		final int index = params.indexOf(param);
		if(index < 0)
		{
			return null;
		}
		final List<PyType> superMethodParameterTypes = new ArrayList<>();
		PySuperMethodsSearch.search(func, context).forEach(psiElement -> {
			if(psiElement instanceof PsiMethod)
			{
				final PsiMethod method = (PsiMethod) psiElement;
				final PsiParameter[] psiParameters = method.getParameterList().getParameters();
				int javaIndex = method.hasModifierProperty(PsiModifier.STATIC) ? index : index - 1; // adjust for 'self' parameter
				if(javaIndex < psiParameters.length)
				{
					PsiType paramType = psiParameters[javaIndex].getType();
					if(paramType instanceof PsiClassType)
					{
						final PsiClass psiClass = ((PsiClassType) paramType).resolve();
						if(psiClass != null)
						{
							superMethodParameterTypes.add(new PyJavaClassType(psiClass, false));
						}
					}
				}
			}
			return true;
		});
		if(superMethodParameterTypes.size() > 0)
		{
			final PyType type = superMethodParameterTypes.get(0);
			if(type != null)
			{
				return Ref.create(type);
			}
		}
		return null;
	}
}
