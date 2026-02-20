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

import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * @author yole
 */
public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression
{
	public PyLambdaExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyLambdaExpression(this);
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		for(PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList())
		{
			PyType type = provider.getCallableType(this, context);
			if(type != null)
			{
				return type;
			}
		}
		return new PyFunctionTypeImpl(this);
	}

	@Nonnull
	public PyParameterList getParameterList()
	{
		PyElement child = childToPsi(PyElementTypes.PARAMETER_LIST_SET, 0);
		if(child == null)
		{
			throw new RuntimeException("parameter list must not be null; text=" + getText());
		}
		//noinspection unchecked
		return (PyParameterList) child;
	}

	@Nullable
	@Override
	public PyType getReturnType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		PyExpression body = getBody();
		return body != null ? context.getType(body) : null;
	}

	@Nullable
	@Override
	public PyType getCallType(@Nonnull TypeEvalContext context, @Nonnull PyCallSiteExpression callSite)
	{
		return context.getReturnType(this);
	}

	@Nullable
	@Override
	public PyType getCallType(@Nullable PyExpression receiver, @Nonnull Map<PyExpression, PyNamedParameter> parameters, @Nonnull TypeEvalContext context)
	{
		return context.getReturnType(this);
	}

	@Nullable
	public PyExpression getBody()
	{
		return PsiTreeUtil.getChildOfType(this, PyExpression.class);
	}

	public PyFunction asMethod()
	{
		return null; // we're never a method
	}

	@Override
	public void subtreeChanged()
	{
		super.subtreeChanged();
		ControlFlowCache.clear(this);
	}

	@Nullable
	@Override
	public String getQualifiedName()
	{
		return null;
	}
}
