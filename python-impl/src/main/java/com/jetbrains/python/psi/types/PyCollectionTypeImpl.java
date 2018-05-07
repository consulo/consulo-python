/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;

/**
 * @author yole
 */
public class PyCollectionTypeImpl extends PyClassTypeImpl implements PyCollectionType
{
	@Nonnull
	private final List<PyType> myElementTypes;

	public PyCollectionTypeImpl(@Nonnull PyClass source, boolean isDefinition, @Nonnull List<PyType> elementTypes)
	{
		super(source, isDefinition);
		myElementTypes = elementTypes;
	}


	@Nullable
	@Override
	public PyType getReturnType(@Nonnull final TypeEvalContext context)
	{
		if(isDefinition())
		{
			return new PyCollectionTypeImpl(getPyClass(), false, myElementTypes);
		}
		return null;
	}

	@Nullable
	@Override
	public PyType getCallType(@Nonnull final TypeEvalContext context, @Nullable final PyCallSiteExpression callSite)
	{
		return getReturnType(context);
	}

	@Nonnull
	@Override
	public List<PyType> getElementTypes(@Nonnull TypeEvalContext context)
	{
		return myElementTypes;
	}

	@Nullable
	public static PyCollectionTypeImpl createTypeByQName(@Nonnull final PsiElement anchor,
			@Nonnull final String classQualifiedName,
			final boolean isDefinition,
			@Nonnull final List<PyType> elementTypes)
	{
		final PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
		if(pyClass == null)
		{
			return null;
		}
		return new PyCollectionTypeImpl(pyClass, isDefinition, elementTypes);
	}

	@Override
	public PyClassType toInstance()
	{
		return myIsDefinition ? withUserDataCopy(new PyCollectionTypeImpl(myClass, false, myElementTypes)) : this;
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
		if(!super.equals(o))
		{
			return false;
		}

		final PyCollectionTypeImpl that = (PyCollectionTypeImpl) o;

		if(!myElementTypes.equals(that.myElementTypes))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = super.hashCode();
		result = 31 * result;
		for(PyType type : myElementTypes)
		{
			result += type != null ? type.hashCode() : 0;
		}
		return result;
	}

	@Nullable
	@Override
	public PyType getIteratedItemType()
	{
		// TODO: Select the parameter type that matches T in Iterable[T]
		return ContainerUtil.getFirstItem(myElementTypes);
	}
}