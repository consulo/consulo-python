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
package com.jetbrains.python.impl.psi.impl.stubs;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassStub;

/**
 * @author max
 */
public class PyClassStubImpl extends StubBase<PyClass> implements PyClassStub
{

	@Nullable
	private final String myName;

	@Nonnull
	private final Map<QualifiedName, QualifiedName> mySuperClasses;

	@Nullable
	private final QualifiedName myMetaClass;

	@Nullable
	private final List<String> mySlots;

	@Nullable
	private final String myDocString;

	public PyClassStubImpl(@Nullable String name,
			@Nullable StubElement parentStub,
			@Nonnull Map<QualifiedName, QualifiedName> superClasses,
			@Nullable QualifiedName metaClass,
			@Nullable List<String> slots,
			@Nullable String docString,
			@Nonnull IStubElementType stubElementType)
	{
		super(parentStub, stubElementType);
		myName = name;
		mySuperClasses = superClasses;
		myMetaClass = metaClass;
		mySlots = slots;
		myDocString = docString;
	}

	@Nullable
	public String getName()
	{
		return myName;
	}

	@Nonnull
	public Map<QualifiedName, QualifiedName> getSuperClasses()
	{
		return mySuperClasses;
	}

	@Nullable
	@Override
	public QualifiedName getMetaClass()
	{
		return myMetaClass;
	}

	@Nullable
	@Override
	public List<String> getSlots()
	{
		return mySlots;
	}

	@Nullable
	@Override
	public String getDocString()
	{
		return myDocString;
	}

	@Override
	public String toString()
	{
		return "PyClassStub(" + myName + ")";
	}
}