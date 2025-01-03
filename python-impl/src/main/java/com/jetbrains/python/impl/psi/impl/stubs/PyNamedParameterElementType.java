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

import java.io.IOException;

import jakarta.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

public class PyNamedParameterElementType extends PyStubElementType<PyNamedParameterStub, PyNamedParameter>
{
	private static final int POSITIONAL_CONTAINER = 1;
	private static final int KEYWORD_CONTAINER = 2;
	private static final int HAS_DEFAULT_VALUE = 4;

	public PyNamedParameterElementType()
	{
		this("NAMED_PARAMETER");
	}

	public PyNamedParameterElementType(@Nonnull @NonNls String debugName)
	{
		super(debugName);
	}

	public PyNamedParameter createPsi(@Nonnull final PyNamedParameterStub stub)
	{
		return new PyNamedParameterImpl(stub);
	}

	@Nonnull
	public PyNamedParameterStub createStub(@Nonnull final PyNamedParameter psi, final StubElement parentStub)
	{
		return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), psi.hasDefaultValue(), psi.getTypeCommentAnnotation(), parentStub,
				getStubElementType());
	}

	@Nonnull
	public PsiElement createElement(@Nonnull final ASTNode node)
	{
		return new PyNamedParameterImpl(node);
	}

	public void serialize(@Nonnull final PyNamedParameterStub stub, @Nonnull final StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getName());

		byte flags = 0;
		if(stub.isPositionalContainer())
		{
			flags |= POSITIONAL_CONTAINER;
		}
		if(stub.isKeywordContainer())
		{
			flags |= KEYWORD_CONTAINER;
		}
		if(stub.hasDefaultValue())
		{
			flags |= HAS_DEFAULT_VALUE;
		}
		dataStream.writeByte(flags);
		dataStream.writeName(stub.getTypeComment());
	}

	@Nonnull
	public PyNamedParameterStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException
	{
		String name = StringRef.toString(dataStream.readName());
		byte flags = dataStream.readByte();
		final StringRef typeComment = dataStream.readName();
		return new PyNamedParameterStubImpl(name, (flags & POSITIONAL_CONTAINER) != 0, (flags & KEYWORD_CONTAINER) != 0, (flags & HAS_DEFAULT_VALUE) != 0, typeComment == null ? null : typeComment
				.getString(), parentStub, getStubElementType());
	}

	@Override
	public boolean shouldCreateStub(ASTNode node)
	{
		final ASTNode paramList = node.getTreeParent();
		if(paramList != null)
		{
			final ASTNode container = paramList.getTreeParent();
			if(container != null && container.getElementType() == PyElementTypes.LAMBDA_EXPRESSION)
			{
				return false;
			}
		}
		return super.shouldCreateStub(node);
	}

	protected IStubElementType getStubElementType()
	{
		return PyElementTypes.NAMED_PARAMETER;
	}
}