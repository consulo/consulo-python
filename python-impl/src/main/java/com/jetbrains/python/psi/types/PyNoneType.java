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
package com.jetbrains.python.psi.types;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;

/**
 * @author yole
 */
public class PyNoneType implements PyType  // TODO must extend ClassType. It's an honest instance.
{
	public static final PyNoneType INSTANCE = new PyNoneType();

	protected PyNoneType()
	{
	}

	@Nullable
	public List<? extends RatedResolveResult> resolveMember(@Nonnull final String name, @Nullable PyExpression location, @Nonnull AccessDirection direction, @Nonnull PyResolveContext resolveContext)
	{
		return null;
	}

	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		return ArrayUtil.EMPTY_OBJECT_ARRAY;
	}

	public String getName()
	{
		return "None";
	}

	@Override
	public boolean isBuiltin()
	{
		return true;
	}

	@Override
	public void assertValid(String message)
	{
	}
}
