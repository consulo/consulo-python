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

import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author dcheryasov
 */
public class PyDecoratorImpl extends StubBasedPsiElementBase<PyDecoratorStub> implements PyDecorator
{

	public PyDecoratorImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyDecoratorImpl(PyDecoratorStub stub)
	{
		super(stub, PyElementTypes.DECORATOR_CALL);
	}

	/**
	 * @return the name of decorator, without the "@". Stub is used if available.
	 */
	@Override
	public String getName()
	{
		final QualifiedName qname = getQualifiedName();
		return qname != null ? qname.getLastComponent() : null;
	}

	@Nullable
	public PyFunction getTarget()
	{
		return PsiTreeUtil.getParentOfType(this, PyFunction.class);
	}

	public boolean isBuiltin()
	{
		ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens());
		if(node != null)
		{
			PyReferenceExpression ref = (PyReferenceExpression) node.getPsi();
			PsiElement target = ref.getReference().resolve();
			return PyBuiltinCache.getInstance(this).isBuiltin(target);
		}
		return false;
	}

	public boolean hasArgumentList()
	{
		final ASTNode arglistNode = getNode().findChildByType(PyElementTypes.ARGUMENT_LIST);
		return (arglistNode != null) && (arglistNode.findChildByType(PyTokenTypes.LPAR) != null);
	}

	public QualifiedName getQualifiedName()
	{
		final PyDecoratorStub stub = getStub();
		if(stub != null)
		{
			return stub.getQualifiedName();
		}
		else
		{
			final PyReferenceExpression node = PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
			if(node != null)
			{
				return node.asQualifiedName();
			}
			return null;
		}
	}

	public PyExpression getCallee()
	{
		try
		{
			return (PyExpression) getFirstChild().getNextSibling(); // skip the @ before call
		}
		catch(NullPointerException npe)
		{ // no sibling
			return null;
		}
		catch(ClassCastException cce)
		{ // error node instead
			return null;
		}
	}

	@Nullable
	public PyArgumentList getArgumentList()
	{
		return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
	}

	@Nonnull
	public PyExpression[] getArguments()
	{
		final PyArgumentList argList = getArgumentList();
		return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
	}

	@Override
	public <T extends PsiElement> T getArgument(int index, Class<T> argClass)
	{
		PyExpression[] args = getArguments();
		return args.length > index && argClass.isInstance(args[index]) ? argClass.cast(args[index]) : null;
	}

	@Override
	public <T extends PsiElement> T getArgument(int index, String keyword, Class<T> argClass)
	{
		final PyExpression argument = getKeywordArgument(keyword);
		if(argument != null)
		{
			return argClass.isInstance(argument) ? argClass.cast(argument) : null;
		}
		return getArgument(index, argClass);
	}

	@Nullable
	@Override
	public <T extends PsiElement> T getArgument(@Nonnull final FunctionParameter parameter, @Nonnull final Class<T> argClass)
	{
		return PyCallExpressionHelper.getArgument(parameter, argClass, this);
	}

	@Override
	public PyExpression getKeywordArgument(String keyword)
	{
		return PyCallExpressionHelper.getKeywordArgument(this, keyword);
	}

	public void addArgument(PyExpression expression)
	{
		PyCallExpressionHelper.addArgument(this, expression);
	}

	public PyMarkedCallee resolveCallee(PyResolveContext resolveContext)
	{
		return resolveCallee(resolveContext, 0);
	}

	public PyMarkedCallee resolveCallee(PyResolveContext resolveContext, int offset)
	{
		PyMarkedCallee callee = PyCallExpressionHelper.resolveCallee(this, resolveContext);
		if(callee == null)
		{
			return null;
		}
		if(!hasArgumentList())
		{
			// NOTE: that +1 thing looks fishy
			callee = new PyMarkedCallee(callee.getCallable(), callee.getModifier(), callee.getImplicitOffset() + 1, callee.isImplicitlyResolved());
		}
		return callee;
	}

	@Nonnull
	@Override
	public PyArgumentsMapping mapArguments(@Nonnull PyResolveContext resolveContext)
	{
		return PyCallExpressionHelper.mapArguments(this, resolveContext, 0);
	}

	@Nonnull
	@Override
	public PyArgumentsMapping mapArguments(@Nonnull PyResolveContext resolveContext, int implicitOffset)
	{
		return PyCallExpressionHelper.mapArguments(this, resolveContext, implicitOffset);
	}

	@Override
	public PyCallable resolveCalleeFunction(PyResolveContext resolveContext)
	{
		return PyCallExpressionHelper.resolveCalleeFunction(this, resolveContext);
	}

	public boolean isCalleeText(@Nonnull String... nameCandidates)
	{
		return PyCallExpressionHelper.isCalleeText(this, nameCandidates);
	}

	@Override
	public boolean isCallee(@Nonnull final FQNamesProvider... name)
	{
		return PyCallExpressionHelper.isCallee(this, name);
	}


	@Override
	public String toString()
	{
		return "PyDecorator: @" + PyUtil.getReadableRepr(getCallee(), true); //getCalledFunctionReference().getReferencedName();
	}

	public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException
	{
		final ASTNode node = getNode();
		final ASTNode nameNode = node.findChildByType(PyTokenTypes.IDENTIFIER);
		if(nameNode != null)
		{
			final ASTNode nameElement = PyUtil.createNewName(this, name);
			node.replaceChild(nameNode, nameElement);
			return this;
		}
		else
		{
			throw new IncorrectOperationException("No name node");
		}
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		return PyCallExpressionHelper.getCallType(this, context);
	}
}
