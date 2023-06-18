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
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;

/**
 * Type of a particular function that is represented as a {@link com.jetbrains.python.psi.PyCallable} in the PSI tree.
 *
 * @author vlan
 */
public class PyFunctionTypeImpl implements PyFunctionType
{
	@Nonnull
	private final PyCallable myCallable;

	public PyFunctionTypeImpl(@Nonnull PyCallable callable)
	{
		myCallable = callable;
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
		return context.getReturnType(myCallable);
	}

	@Nullable
	@Override
	public PyType getCallType(@Nonnull TypeEvalContext context, @Nonnull PyCallSiteExpression callSite)
	{
		return myCallable.getCallType(context, callSite);
	}

	@Nullable
	@Override
	public List<PyCallableParameter> getParameters(@Nonnull TypeEvalContext context)
	{
		final List<PyCallableParameter> result = new ArrayList<>();
		for(PyParameter parameter : myCallable.getParameterList().getParameters())
		{
			result.add(new PyCallableParameterImpl(parameter));
		}
		return result;
	}

	@Override
	public List<? extends RatedResolveResult> resolveMember(@Nonnull String name, @Nullable PyExpression location, @Nonnull AccessDirection direction, @Nonnull PyResolveContext resolveContext)
	{
		final PyClassType delegate = selectFakeType(location, resolveContext.getTypeEvalContext());
		if(delegate == null)
		{
			return Collections.emptyList();
		}
		return delegate.resolveMember(name, location, direction, resolveContext);
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
		final PyClassType delegate;
		if(location instanceof PyReferenceExpression)
		{
			delegate = selectFakeType(((PyReferenceExpression) location).getQualifier(), typeEvalContext);
		}
		else
		{
			delegate = PyBuiltinCache.getInstance(getCallable()).getObjectType(PyNames.FAKE_FUNCTION);
		}
		if(delegate == null)
		{
			return ArrayUtil.EMPTY_OBJECT_ARRAY;
		}
		return delegate.getCompletionVariants(completionPrefix, location, context);
	}

	/**
	 * Select either {@link PyNames#FAKE_FUNCTION} or {@link PyNames#FAKE_METHOD} fake class depending on concrete reference used and
	 * language level. Will fallback to fake function type.
	 */
	@Nullable
	private PyClassTypeImpl selectFakeType(@Nullable PyExpression location, @Nonnull TypeEvalContext context)
	{
		final String fakeClassName;
		if(location instanceof PyReferenceExpression && isBoundMethodReference((PyReferenceExpression) location, context))
		{
			fakeClassName = PyNames.FAKE_METHOD;
		}
		else
		{
			fakeClassName = PyNames.FAKE_FUNCTION;
		}
		return PyBuiltinCache.getInstance(getCallable()).getObjectType(fakeClassName);
	}

	private boolean isBoundMethodReference(@Nonnull PyReferenceExpression location, @Nonnull TypeEvalContext context)
	{
		final PyFunction function = as(getCallable(), PyFunction.class);
		final boolean isNonStaticMethod = function != null && function.getContainingClass() != null && function.getModifier() != STATICMETHOD;
		if(isNonStaticMethod)
		{
			// In Python 2 unbound methods have __method fake type
			if(LanguageLevel.forElement(location).isOlderThan(LanguageLevel.PYTHON30))
			{
				return true;
			}
			final PyExpression qualifier;
			if(location.isQualified())
			{
				qualifier = location.getQualifier();
			}
			else
			{
				final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
				final QualifiedResolveResult resolveResult = location.followAssignmentsChain(resolveContext);
				final List<PyExpression> qualifiers = resolveResult.getQualifiers();
				qualifier = ContainerUtil.isEmpty(qualifiers) ? null : qualifiers.get(qualifiers.size() - 1);
			}
			if(qualifier != null)
			{
				//noinspection ConstantConditions
				final PyType qualifierType = PyTypeChecker.toNonWeakType(context.getType(qualifier), context);
				if(isInstanceType(qualifierType))
				{
					return true;
				}
				else if(qualifierType instanceof PyUnionType)
				{
					for(PyType type : ((PyUnionType) qualifierType).getMembers())
					{
						if(isInstanceType(type))
						{
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean isInstanceType(@Nullable PyType type)
	{
		return type instanceof PyClassType && !((PyClassType) type).isDefinition();
	}

	@Override
	public String getName()
	{
		return "function";
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
	@Nonnull
	public PyCallable getCallable()
	{
		return myCallable;
	}

	@Nullable
	public static String getParameterName(@Nonnull PyNamedParameter namedParameter)
	{
		String name = namedParameter.getName();
		if(namedParameter.isPositionalContainer())
		{
			name = "*" + name;
		}
		else if(namedParameter.isKeywordContainer())
		{
			name = "**" + name;
		}
		return name;
	}
}
