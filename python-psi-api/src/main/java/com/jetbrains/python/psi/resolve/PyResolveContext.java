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
package com.jetbrains.python.psi.resolve;

import jakarta.annotation.Nonnull;

import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyResolveContext
{
	private final boolean myAllowImplicits;
	private final boolean myAllowProperties;
	private final boolean myAllowRemote;
	private final TypeEvalContext myTypeEvalContext;


	private PyResolveContext(boolean allowImplicits, boolean allowProperties)
	{
		myAllowImplicits = allowImplicits;
		myAllowProperties = allowProperties;
		myTypeEvalContext = null;
		myAllowRemote = false;
	}


	private PyResolveContext(boolean allowImplicits, boolean allowProperties, boolean allowRemote, TypeEvalContext typeEvalContext)
	{
		myAllowImplicits = allowImplicits;
		myAllowProperties = allowProperties;
		myAllowRemote = allowRemote;
		myTypeEvalContext = typeEvalContext;
	}

	public boolean allowImplicits()
	{
		return myAllowImplicits;
	}

	public boolean allowProperties()
	{
		return myAllowProperties;
	}

	public boolean allowRemote()
	{
		return myAllowRemote;
	}

	private static final PyResolveContext ourDefaultContext = new PyResolveContext(true, true);
	private static final PyResolveContext ourNoImplicitsContext = new PyResolveContext(false, true);
	private static final PyResolveContext ourNoPropertiesContext = new PyResolveContext(false, false);

	public static PyResolveContext defaultContext()
	{
		return ourDefaultContext;
	}

	public static PyResolveContext noImplicits()
	{
		return ourNoImplicitsContext;
	}

	public static PyResolveContext noProperties()
	{
		return ourNoPropertiesContext;
	}

	public PyResolveContext withTypeEvalContext(@Nonnull TypeEvalContext context)
	{
		return new PyResolveContext(myAllowImplicits, myAllowProperties, myAllowRemote, context);
	}

	public PyResolveContext withoutImplicits()
	{
		return new PyResolveContext(false, myAllowProperties, myAllowRemote, myTypeEvalContext);
	}

	public PyResolveContext withRemote()
	{
		return new PyResolveContext(myAllowImplicits, myAllowProperties, true, myTypeEvalContext);
	}

	public TypeEvalContext getTypeEvalContext()
	{
		return myTypeEvalContext != null ? myTypeEvalContext : TypeEvalContext.codeInsightFallback(null);
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

		PyResolveContext that = (PyResolveContext) o;

		if(myAllowImplicits != that.myAllowImplicits)
		{
			return false;
		}
		if(myTypeEvalContext != null ? !myTypeEvalContext.equals(that.myTypeEvalContext) : that.myTypeEvalContext != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = (myAllowImplicits ? 1 : 0);
		result = 31 * result + (myTypeEvalContext != null ? myTypeEvalContext.hashCode() : 0);
		return result;
	}
}
