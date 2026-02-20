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
package com.jetbrains.python.impl.psi.impl.stubs;

import java.io.IOException;

import jakarta.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import consulo.language.ast.ASTNode;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionStub;

/**
 * @author max
 */
public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction>
{
	public PyFunctionElementType()
	{
		this("FUNCTION_DECLARATION");
	}

	public PyFunctionElementType(@Nonnull @NonNls String debugName)
	{
		super(debugName);
	}

	@Nonnull
	public PsiElement createElement(@Nonnull ASTNode node)
	{
		return new PyFunctionImpl(node);
	}

	public PyFunction createPsi(@Nonnull PyFunctionStub stub)
	{
		return new PyFunctionImpl(stub);
	}

	@Nonnull
	public PyFunctionStub createStub(@Nonnull PyFunction psi, StubElement parentStub)
	{
		PyFunctionImpl function = (PyFunctionImpl) psi;
		String message = function.extractDeprecationMessage();
		PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
		String typeComment = function.getTypeCommentAnnotation();
		return new PyFunctionStubImpl(psi.getName(), PyPsiUtils.strValue(docStringExpression), message, function.isAsync(), typeComment, parentStub, getStubElementType());
	}

	public void serialize(@Nonnull PyFunctionStub stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getName());
		dataStream.writeUTFFast(StringUtil.notNullize(stub.getDocString()));
		dataStream.writeName(stub.getDeprecationMessage());
		dataStream.writeBoolean(stub.isAsync());
		dataStream.writeName(stub.getTypeComment());
	}

	@Nonnull
	public PyFunctionStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		String name = StringRef.toString(dataStream.readName());
		String docString = dataStream.readUTFFast();
		StringRef deprecationMessage = dataStream.readName();
		boolean isAsync = dataStream.readBoolean();
		StringRef typeComment = dataStream.readName();
		return new PyFunctionStubImpl(name, StringUtil.nullize(docString), deprecationMessage == null ? null : deprecationMessage.getString(), isAsync, typeComment == null ? null : typeComment
				.getString(), parentStub, getStubElementType());
	}

	public void indexStub(@Nonnull PyFunctionStub stub, @Nonnull IndexSink sink)
	{
		String name = stub.getName();
		if(name != null)
		{
			sink.occurrence(PyFunctionNameIndex.KEY, name);
		}
	}

	protected IStubElementType getStubElementType()
	{
		return PyElementTypes.FUNCTION_DECLARATION;
	}
}