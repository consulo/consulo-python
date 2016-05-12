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
import org.mustbe.consulo.RequiredReadAction;
import org.mustbe.consulo.dotnet.psi.DotNetNamedElement;
import org.mustbe.consulo.dotnet.psi.DotNetTypeDeclaration;
import org.mustbe.consulo.dotnet.resolve.DotNetTypeRef;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.IconDescriptorUpdaters;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.ResolveResultList;
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
		ResolveResultList resultList = new ResolveResultList();
		for(DotNetNamedElement dotNetNamedElement : myClass.getMembers())
		{
			String name1 = dotNetNamedElement.getName();
			if(!name.equals(name1))
			{
				continue;
			}
			resultList.poke(dotNetNamedElement, RatedResolveResult.RATE_NORMAL);
		}
		return resultList;
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		List<Object> variants = new ArrayList<Object>();
		for(PsiElement child : myClass.getMembers())
		{
			if(child instanceof PsiNamedElement)
			{
				variants.add(LookupElementBuilder.create((PsiNamedElement) child).withIcon(IconDescriptorUpdaters.getIcon(child, 0)));
			}
		}
		return ArrayUtil.toObjectArray(variants);
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
		return false;
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
	@RequiredReadAction
	public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context)
	{
		final List<PyClassLikeType> result = new ArrayList<PyClassLikeType>();
		for(DotNetTypeRef typeRef : myClass.getExtendTypeRefs())
		{
			PsiElement resolve = typeRef.resolve().getElement();
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
