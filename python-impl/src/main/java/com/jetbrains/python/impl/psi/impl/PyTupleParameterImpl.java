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
package com.jetbrains.python.impl.psi.impl;

import jakarta.annotation.Nonnull;
import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;

/**
 * Represents a tuple parameter as stubbed element.
 */
public class PyTupleParameterImpl extends PyBaseElementImpl<PyTupleParameterStub> implements PyTupleParameter
{

	public PyTupleParameterImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyTupleParameterImpl(PyTupleParameterStub stub)
	{
		super(stub, PyElementTypes.TUPLE_PARAMETER);
	}

	public PyNamedParameter getAsNamed()
	{
		return null;  // we're not named
	}

	public PyTupleParameter getAsTuple()
	{
		return this;
	}

	public PyExpression getDefaultValue()
	{
		ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
		if(nodes.length > 0)
		{
			return (PyExpression) nodes[0].getPsi();
		}
		return null;
	}

	public boolean hasDefaultValue()
	{
		final PyTupleParameterStub stub = getStub();
		if(stub != null)
		{
			return stub.hasDefaultValue();
		}
		return getDefaultValue() != null;
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyTupleParameter(this);
	}

	@Nonnull
	public PyParameter[] getContents()
	{
		return getStubOrPsiChildren(PythonDialectsTokenSetProvider.INSTANCE.getParameterTokens(), new PyParameter[0]);
	}

	@Override
	public boolean isSelf()
	{
		return false;
	}

	@Override
	public ItemPresentation getPresentation()
	{
		return new PyElementPresentation(this);
	}
}
