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

package consulo.ironPython.psi.impl;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.dotnet.psi.DotNetLikeMethodDeclaration;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 * @author VISTALL
 */
public class PyDotNetMethodType implements PyCallableType
{
	private final DotNetLikeMethodDeclaration myMethod;

	public PyDotNetMethodType(DotNetLikeMethodDeclaration method)
	{
		myMethod = method;
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
		return null;
	}

	@Nullable
	@Override
	public PyType getCallType(@Nonnull TypeEvalContext context, @Nonnull PyCallSiteExpression callSite)
	{
		return PyDotNetTypeProvider.asPyType(myMethod.getReturnTypeRef());
	}

	@Nullable
	@Override
	public List<PyCallableParameter> getParameters(@Nonnull TypeEvalContext context)
	{
		return null;
	}

	@Nullable
	@Override
	public List<? extends RatedResolveResult> resolveMember(@Nonnull String name, @Nullable PyExpression location,
			@Nonnull AccessDirection direction, @Nonnull PyResolveContext resolveContext)
	{
		return Collections.emptyList();
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		return ArrayUtil.EMPTY_OBJECT_ARRAY;
	}

	@Nullable
	@Override
	public String getName()
	{
		return myMethod.getPresentableQName();
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
