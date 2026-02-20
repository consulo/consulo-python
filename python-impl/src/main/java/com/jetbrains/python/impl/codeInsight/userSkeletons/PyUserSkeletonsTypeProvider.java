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
package com.jetbrains.python.impl.codeInsight.userSkeletons;

import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author vlan
 */
@ExtensionImpl
public class PyUserSkeletonsTypeProvider extends PyTypeProviderBase
{
	@Override
	public Ref<PyType> getParameterType(@Nonnull PyNamedParameter param, @Nonnull PyFunction func, @Nonnull TypeEvalContext context)
	{
		String name = param.getName();
		if(name != null)
		{
			PyFunction functionSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(func, context);
			if(functionSkeleton != null)
			{
				PyNamedParameter paramSkeleton = functionSkeleton.getParameterList().findParameterByName(name);
				if(paramSkeleton != null)
				{
					PyType type = context.getType(paramSkeleton);
					if(type != null)
					{
						return Ref.create(type);
					}
				}
			}
		}
		return null;
	}

	@Nullable
	@Override
	public Ref<PyType> getReturnType(@Nonnull PyCallable callable, @Nonnull TypeEvalContext context)
	{
		PyCallable callableSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(callable, context);
		if(callableSkeleton != null)
		{
			PyType type = context.getReturnType(callableSkeleton);
			if(type != null)
			{
				return Ref.create(type);
			}
		}
		return null;
	}

	@Override
	public PyType getReferenceType(@Nonnull PsiElement target, TypeEvalContext context, @Nullable PsiElement anchor)
	{
		if(target instanceof PyTargetExpression)
		{
			PyTargetExpression targetSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext((PyTargetExpression) target, context);
			if(targetSkeleton != null)
			{
				return context.getType(targetSkeleton);
			}
		}
		return null;
	}

	@Nullable
	@Override
	public PyType getCallableType(@Nonnull PyCallable callable, @Nonnull TypeEvalContext context)
	{
		PyCallable callableSkeleton = PyUserSkeletonsUtil.getUserSkeletonWithContext(callable, context);
		if(callableSkeleton != null)
		{
			return context.getType(callableSkeleton);
		}
		return null;
	}
}
