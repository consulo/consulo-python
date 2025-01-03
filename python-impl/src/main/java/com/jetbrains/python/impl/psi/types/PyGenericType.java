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
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author vlan
 */
public class PyGenericType implements PyType
{
	@Nonnull
	private final String myName;
	@Nullable
	private PyType myBound;

	public PyGenericType(@Nonnull String name, @Nullable PyType bound)
	{
		myName = name;
		myBound = bound;
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

	@Nonnull
	@Override
	public String getName()
	{
		if(myBound instanceof PyUnionType)
		{
			final PyUnionType bounds = (PyUnionType) myBound;
			final String boundsString = StringUtil.join(bounds.getMembers(), type -> type != null ? type.getName() : PyNames.UNKNOWN_TYPE, ", ");
			return "TypeVar('" + myName + "', " + boundsString + ")";
		}
		else
		{
			return "TypeVar('" + myName + "')";
		}
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

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}
		final PyGenericType type = (PyGenericType) o;
		return myName.equals(type.myName);
	}

	@Override
	public int hashCode()
	{
		return myName.hashCode();
	}

	@Nonnull
	@Override
	public String toString()
	{
		return "PyGenericType: " + getName();
	}

	@Nullable
	public PyType getBound()
	{
		return myBound;
	}
}
