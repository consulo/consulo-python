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
package com.jetbrains.python.psi;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.PythonFileType;

/**
 * @author max
 */
public abstract class PyStubElementType<StubT extends StubElement, PsiT extends PyElement> extends IStubElementType<StubT, PsiT>
{
	public PyStubElementType(@Nonnull @NonNls String debugName)
	{
		super(debugName, PythonFileType.INSTANCE.getLanguage());
	}

	@Override
	public String toString()
	{
		return "Py:" + super.toString();
	}

	@Nonnull
	public abstract PsiElement createElement(@Nonnull final ASTNode node);

	public void indexStub(@Nonnull final StubT stub, @Nonnull final IndexSink sink)
	{
	}

	@Nonnull
	public String getExternalId()
	{
		return "py." + super.toString();
	}
}