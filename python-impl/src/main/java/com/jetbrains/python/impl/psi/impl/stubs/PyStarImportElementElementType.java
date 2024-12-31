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

import java.io.IOException;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyStarImportElementImpl;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;

/**
 * @author vlan
 */
public class PyStarImportElementElementType extends PyStubElementType<PyStarImportElementStub, PyStarImportElement>
{
	public PyStarImportElementElementType()
	{
		super("STAR_IMPORT_ELEMENT");
	}

	@Nonnull
	public PsiElement createElement(@Nonnull final ASTNode node)
	{
		return new PyStarImportElementImpl(node);
	}

	public PyStarImportElement createPsi(@Nonnull final PyStarImportElementStub stub)
	{
		return new PyStarImportElementImpl(stub);
	}

	@Nonnull
	public PyStarImportElementStub createStub(@Nonnull final PyStarImportElement psi, final StubElement parentStub)
	{
		return new PyStarImportElementStubImpl(parentStub);
	}

	public void serialize(@Nonnull final PyStarImportElementStub stub, @Nonnull final StubOutputStream dataStream) throws IOException
	{
	}

	@Nonnull
	public PyStarImportElementStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException
	{
		return new PyStarImportElementStubImpl(parentStub);
	}
}
