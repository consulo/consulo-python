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

/*
 * @author max
 */
package com.jetbrains.python.impl.psi.impl.stubs;

import jakarta.annotation.Nullable;

import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

public class PyNamedParameterStubImpl extends StubBase<PyNamedParameter> implements PyNamedParameterStub
{
	private final String myName;
	private final boolean myPositionalContainer;
	private final boolean myKeywordContainer;
	private final boolean myHasDefaultValue;
	private final String myTypeCommentAnnotation;

	public PyNamedParameterStubImpl(String name,
			boolean isPositionalContainer,
			boolean isKeywordContainer,
			boolean hasDefaultValue,
			@Nullable String typeCommentAnnotation,
			StubElement parent,
			IStubElementType stubElementType)
	{
		super(parent, stubElementType);
		myName = name;
		myPositionalContainer = isPositionalContainer;
		myKeywordContainer = isKeywordContainer;
		myHasDefaultValue = hasDefaultValue;
		myTypeCommentAnnotation = typeCommentAnnotation;
	}

	public boolean isPositionalContainer()
	{
		return myPositionalContainer;
	}

	public boolean isKeywordContainer()
	{
		return myKeywordContainer;
	}

	public boolean hasDefaultValue()
	{
		return myHasDefaultValue;
	}

	@Nullable
	@Override
	public String getTypeComment()
	{
		return myTypeCommentAnnotation;
	}

	public String getName()
	{
		return myName;
	}

	@Override
	public String toString()
	{
		return "PyNamedParameterStub(" + myName + ")";
	}
}