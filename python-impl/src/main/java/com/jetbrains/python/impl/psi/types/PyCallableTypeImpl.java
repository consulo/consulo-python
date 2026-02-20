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
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author vlan
 */
public class PyCallableTypeImpl implements PyCallableType
{
	@Nullable
	private final List<PyCallableParameter> myParameters;
	@Nullable
	private final PyType myReturnType;

	public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters, @Nullable PyType returnType)
	{
		myParameters = parameters;
		myReturnType = returnType;
	}

	@Override
	public boolean isCallable()
	{
		return true;
	}

	@Nullable
	@Override
	public PyType getReturnType(@Nonnull TypeEvalContext context)
	{
		return myReturnType;
	}

	@Nullable
	@Override
	public PyType getCallType(@Nonnull TypeEvalContext context, @Nonnull PyCallSiteExpression callSite)
	{
		return myReturnType;
	}

	@Nullable
	@Override
	public List<PyCallableParameter> getParameters(@Nonnull TypeEvalContext context)
	{
		return myParameters;
	}

	@Nullable
	@Override
	public List<? extends RatedResolveResult> resolveMember(@Nonnull String name, @Nullable PyExpression location, @Nonnull AccessDirection direction, @Nonnull PyResolveContext resolveContext)
	{
		return null;
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		return new Object[0];
	}

	@Nullable
	@Override
	public String getName()
	{
		TypeEvalContext context = TypeEvalContext.codeInsightFallback(null);
		return String.format("(%s) -> %s", myParameters != null ? StringUtil.join(myParameters, param -> {
			if(param != null)
			{
				StringBuilder builder = new StringBuilder();
				String name = param.getName();
				PyType type = param.getType(context);
				if(name != null)
				{
					builder.append(name);
					if(type != null)
					{
						builder.append(": ");
					}
				}
				builder.append(type != null ? type.getName() : PyNames.UNKNOWN_TYPE);
				return builder.toString();
			}
			return PyNames.UNKNOWN_TYPE;
		}, ", ") : "...", myReturnType != null ? myReturnType.getName() : PyNames.UNKNOWN_TYPE);
	}

	@Override
	public boolean isBuiltin()
	{
		return false;
	}

	@Override
	public void assertValid(String message)
	{
	}
}
