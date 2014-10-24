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

package org.mustbe.consulo.ironPython.psi.impl;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.dotnet.psi.DotNetTypeDeclaration;
import org.mustbe.consulo.dotnet.resolve.DotNetTypeRef;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyDotNetClassType implements PyClassLikeType
{
	private final DotNetTypeDeclaration myClass;
	private final boolean myDefinition;

	public PyDotNetClassType(final DotNetTypeDeclaration aClass, boolean definition)
	{
		myClass = aClass;
		myDefinition = definition;
	}

	@Override
	@Nullable
	public List<? extends RatedResolveResult> resolveMember(@NotNull final String name, PyExpression location, @NotNull AccessDirection direction,
			@NotNull PyResolveContext resolveContext)
	{
		return resolveMember(name, location, direction, resolveContext, true);
	}

	@Nullable
	@Override
	public List<? extends RatedResolveResult> resolveMember(@NotNull String name, @Nullable PyExpression location,
			@NotNull AccessDirection direction, @NotNull PyResolveContext resolveContext, boolean inherited)
	{
	/*final PsiMethod[] methods = myClass.findMethodsByName(name, inherited);
		if(methods.length > 0)
		{
			ResolveResultList resultList = new ResolveResultList();
			for(PsiMethod method : methods)
			{
				resultList.poke(method, RatedResolveResult.RATE_NORMAL);
			}
			return resultList;
		}
		final PsiField field = myClass.findFieldByName(name, inherited);
		if(field != null)
		{
			return ResolveResultList.to(field);
		}   */
		return null;
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location);
		myClass.processDeclarations(processor, ResolveState.initial(), null, location);
		return processor.getResult();
	}

	@Override
	public String getName()
	{
		if(myClass != null)
		{
			return myClass.getName();
		}
		else
		{
			return null;
		}
	}

	@Override
	public boolean isBuiltin(TypeEvalContext context)
	{
		return false;  // TODO: JDK's types could be considered built-in.
	}

	@Override
	public void assertValid(String message)
	{
	}

	@Override
	public boolean isCallable()
	{
		return myDefinition;
	}

	@Nullable
	@Override
	public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite)
	{
		if(myDefinition)
		{
			return new PyDotNetClassType(myClass, false);
		}
		return null;
	}

	@Nullable
	@Override
	public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context)
	{
		return null;
	}

	@Override
	public boolean isDefinition()
	{
		return myDefinition;
	}

	@Override
	public PyClassLikeType toInstance()
	{
		return myDefinition ? new PyDotNetClassType(myClass, false) : this;
	}

	@Nullable
	@Override
	public String getClassQName()
	{
		return myClass.getPresentableQName();
	}

	@NotNull
	@Override
	public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context)
	{
		final List<PyClassLikeType> result = new ArrayList<PyClassLikeType>();
		for(DotNetTypeRef typeRef : myClass.getExtendTypeRefs())
		{
			PsiElement resolve = typeRef.resolve(myClass).getElement();
			if(resolve instanceof DotNetTypeDeclaration)
			{
				result.add(new PyDotNetClassType((DotNetTypeDeclaration) resolve, myDefinition));
			}
		}
		return result;
	}

	@Override
	public boolean isValid()
	{
		return myClass.isValid();
	}

	public DotNetTypeDeclaration getPsiClass()
	{
		return myClass;
	}
}
