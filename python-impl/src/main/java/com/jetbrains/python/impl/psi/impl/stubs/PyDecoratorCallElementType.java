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

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.impl.psi.impl.PyDecoratorImpl;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.impl.psi.stubs.PyDecoratorStubIndex;

/**
 * Actual serialized data of a decorator call.
 * User: dcheryasov
 * Date: Dec 18, 2008 9:09:57 PM
 */
public class PyDecoratorCallElementType extends PyStubElementType<PyDecoratorStub, PyDecorator>
{
	public PyDecoratorCallElementType()
	{
		super("DECORATOR_CALL");
	}

	@Nonnull
	public PsiElement createElement(@Nonnull ASTNode node)
	{
		return new PyDecoratorImpl(node);
	}

	public PyDecorator createPsi(@Nonnull PyDecoratorStub stub)
	{
		return new PyDecoratorImpl(stub);
	}

	@Nonnull
	public PyDecoratorStub createStub(@Nonnull PyDecorator psi, StubElement parentStub)
	{
		return new PyDecoratorStubImpl(psi.getQualifiedName(), parentStub);
	}

	public void serialize(@Nonnull PyDecoratorStub stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
		QualifiedName.serialize(stub.getQualifiedName(), dataStream);
	}

	@Override
	public void indexStub(@Nonnull final PyDecoratorStub stub, @Nonnull final IndexSink sink)
	{
		// Index decorators stub by name (todo: index by FQDN as well!)
		final QualifiedName qualifiedName = stub.getQualifiedName();
		if(qualifiedName != null)
		{
			sink.occurrence(PyDecoratorStubIndex.KEY, qualifiedName.toString());
		}
	}

	@Nonnull
	public PyDecoratorStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		QualifiedName q_name = QualifiedName.deserialize(dataStream);
		return new PyDecoratorStubImpl(q_name, parentStub);
	}
}
