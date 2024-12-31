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
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.function.Processor;
import consulo.dotnet.psi.DotNetNamedElement;
import consulo.dotnet.psi.DotNetTypeDeclaration;
import consulo.dotnet.psi.resolve.DotNetTypeRef;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

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
	public List<? extends RatedResolveResult> resolveMember(@Nonnull final String name, PyExpression location, @Nonnull AccessDirection direction,
			@Nonnull PyResolveContext resolveContext)
	{
		return resolveMember(name, location, direction, resolveContext, true);
	}

	@Nullable
	@Override
	public List<? extends RatedResolveResult> resolveMember(@Nonnull String name, @Nullable PyExpression location,
			@Nonnull AccessDirection direction, @Nonnull PyResolveContext resolveContext, boolean inherited)
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
	public void visitMembers(@Nonnull Processor<PsiElement> processor, boolean inherited, @Nonnull TypeEvalContext context)
	{
		for(DotNetNamedElement dotNetNamedElement : myClass.getMembers())
		{
			processor.process(dotNetNamedElement);
		}
	}

	@Nonnull
	@Override
	public Set<String> getMemberNames(boolean inherited, @Nonnull TypeEvalContext context)
	{
		Set<String> names = new HashSet<>();
		for(DotNetNamedElement dotNetNamedElement : myClass.getMembers())
		{
			names.add(dotNetNamedElement.getName());
		}
		return names;
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
	public boolean isBuiltin()
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
	public PyType getReturnType(@Nonnull TypeEvalContext context)
	{
		return null;
	}

	@Nullable
	@Override
	public PyType getCallType(@Nonnull TypeEvalContext context, @Nonnull PyCallSiteExpression callSite)
	{
		if(myDefinition)
		{
			return new PyDotNetClassType(myClass, false);
		}
		return null;
	}

	@Nullable
	@Override
	public List<PyCallableParameter> getParameters(@Nonnull TypeEvalContext context)
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

	@Nonnull
	@Override
	@RequiredReadAction
	public List<PyClassLikeType> getSuperClassTypes(@Nonnull TypeEvalContext context)
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

	@Nullable
	@Override
	public PyClassLikeType getMetaClassType(@Nonnull TypeEvalContext context, boolean inherited)
	{
		return null;
	}

	public DotNetTypeDeclaration getPsiClass()
	{
		return myClass;
	}

	@Nonnull
	@Override
	public List<PyClassLikeType> getAncestorTypes(@Nonnull TypeEvalContext context)
	{
		return Collections.emptyList();
	}
}
