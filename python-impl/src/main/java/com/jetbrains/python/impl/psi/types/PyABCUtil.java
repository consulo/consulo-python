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
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author vlan
 */
public class PyABCUtil
{
	private PyABCUtil()
	{
	}

	public static boolean isSubclass(@Nonnull PyClass subClass, @Nonnull PyClass superClass, @Nullable TypeEvalContext context)
	{
		final String superName = superClass.getName();
		if(superName != null)
		{
			return isSubclass(subClass, superName, true, context);
		}
		return false;
	}

	public static boolean isSubclass(@Nonnull PyClass subClass, @Nonnull String superClassName, @Nullable TypeEvalContext context)
	{
		return isSubclass(subClass, superClassName, true, context);
	}

	public static boolean isSubclass(@Nonnull PyClass subClass, @Nonnull String superClassName, boolean inherited, @Nullable TypeEvalContext context)
	{
		if(PyNames.CALLABLE.equals(superClassName))
		{
			return hasMethod(subClass, PyNames.CALL, inherited, context);
		}
		if(PyNames.HASHABLE.equals(superClassName))
		{
			return hasMethod(subClass, PyNames.HASH, inherited, context);
		}
		final boolean hasIter = hasMethod(subClass, PyNames.ITER, inherited, context);
		final boolean hasGetItem = hasMethod(subClass, PyNames.GETITEM, inherited, context);
		if(PyNames.ITERABLE.equals(superClassName))
		{
			return hasIter || hasGetItem;
		}
		if(PyNames.ITERATOR.equals(superClassName))
		{
			return (hasIter && (hasMethod(subClass, PyNames.NEXT, inherited, context) || hasMethod(subClass, PyNames.DUNDER_NEXT, inherited, context))) || hasGetItem;
		}
		final boolean isSized = hasMethod(subClass, PyNames.LEN, inherited, context);
		if(PyNames.SIZED.equals(superClassName))
		{
			return isSized;
		}
		final boolean isContainer = hasMethod(subClass, PyNames.CONTAINS, inherited, context);
		if(PyNames.CONTAINER.equals(superClassName))
		{
			return isContainer;
		}
		if(PyNames.SEQUENCE.equals(superClassName))
		{
			return isSized && hasIter && isContainer && hasGetItem;
		}
		if(PyNames.MAPPING.equals(superClassName))
		{
			return isSized && hasIter && isContainer && hasGetItem && hasMethod(subClass, PyNames.KEYS, inherited, context);
		}
		if(PyNames.MUTABLE_MAPPING.equals(superClassName))
		{
			final boolean hasSetItem = hasMethod(subClass, PyNames.SETITEM, inherited, context);
			final boolean hasUpdate = hasMethod(subClass, PyNames.UPDATE, inherited, context);
			return isSized && hasIter && isContainer && hasGetItem && hasSetItem && hasUpdate;
		}
		if(PyNames.ABC_SET.equals(superClassName))
		{
			return isSized && hasIter && isContainer;
		}
		if(PyNames.ABC_MUTABLE_SET.equals(superClassName))
		{
			return isSized && hasIter && isContainer &&
					hasMethod(subClass, "discard", inherited, context) &&
					hasMethod(subClass, "add", inherited, context);
		}
		if(PyNames.ABC_COMPLEX.equals(superClassName))
		{
			return hasMethod(subClass, "__complex__", inherited, context);
		}
		if(PyNames.ABC_REAL.equals(superClassName))
		{
			return hasMethod(subClass, "__float__", inherited, context);
		}
		if(PyNames.ABC_INTEGRAL.equals(superClassName))
		{
			return hasMethod(subClass, "__int__", inherited, context);
		}
		if(PyNames.ABC_NUMBER.equals(superClassName) && "Decimal".equals(subClass.getName()))
		{
			return true;
		}
		if(PyNames.ASYNC_ITERABLE.equals(superClassName))
		{
			return hasMethod(subClass, PyNames.AITER, inherited, context);
		}
		return false;
	}

	public static boolean isSubtype(@Nonnull PyType type, @Nonnull String superClassName, @Nonnull TypeEvalContext context)
	{
		if(type instanceof PyStructuralType)
		{
			// TODO: Convert abc types to structural types and check them properly
			return true;
		}
		if(type instanceof PyClassType)
		{
			final PyClassType classType = (PyClassType) type;
			final PyClass pyClass = classType.getPyClass();
			if(classType.isDefinition())
			{
				final PyClassLikeType metaClassType = classType.getMetaClassType(context, true);
				if(metaClassType instanceof PyClassType)
				{
					final PyClassType metaClass = (PyClassType) metaClassType;
					return isSubclass(metaClass.getPyClass(), superClassName, true, context);
				}
			}
			else
			{
				return isSubclass(pyClass, superClassName, true, context);
			}
		}
		if(type instanceof PyUnionType)
		{
			final PyUnionType unionType = (PyUnionType) type;
			for(PyType m : unionType.getMembers())
			{
				if(m != null)
				{
					if(!isSubtype(m, superClassName, context))
					{
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}

	private static boolean hasMethod(PyClass cls, String name, boolean inherited, TypeEvalContext context)
	{
		return cls.findMethodByName(name, inherited, context) != null || cls.findClassAttribute(name, inherited, context) != null;
	}
}
