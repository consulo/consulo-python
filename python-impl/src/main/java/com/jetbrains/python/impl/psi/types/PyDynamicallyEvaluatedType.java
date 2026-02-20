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
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
public class PyDynamicallyEvaluatedType extends PyUnionType
{
	private PyDynamicallyEvaluatedType(@Nonnull Collection<PyType> members)
	{
		super(members);
	}

	@Nonnull
	public static PyDynamicallyEvaluatedType create(@Nonnull PyType type)
	{
		List<PyType> members = new ArrayList<>();
		if(type instanceof PyUnionType)
		{
			PyUnionType unionType = (PyUnionType) type;
			members.addAll(unionType.getMembers());
			if(!unionType.isWeak())
			{
				members.add(null);
			}
		}
		else
		{
			members.add(type);
			members.add(null);
		}
		return new PyDynamicallyEvaluatedType(members);
	}

	@Override
	public String getName()
	{
		PyType res = excludeNull(TypeEvalContext.codeInsightFallback(null));
		return res != null ? res.getName() : PyNames.UNKNOWN_TYPE;
	}
}
