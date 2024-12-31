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

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.collect.Lists;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

/**
 * @author dcheryasov
 */
public class PyExceptPartImpl extends PyBaseElementImpl<PyExceptPartStub> implements PyExceptPart
{
	public PyExceptPartImpl(ASTNode astNode)
	{
		super(astNode);
	}

	public PyExceptPartImpl(PyExceptPartStub stub)
	{
		super(stub, PyElementTypes.EXCEPT_PART);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyExceptBlock(this);
	}

	@Nullable
	public PyExpression getExceptClass()
	{
		return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
	}

	@Nullable
	public PyExpression getTarget()
	{
		return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
	}

	@Nonnull
	public PyStatementList getStatementList()
	{
		return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
	}

	@Nonnull
	public List<PsiNamedElement> getNamedElements()
	{
		final List<PyExpression> expressions = PyUtil.flattenedParensAndStars(getTarget());
		final List<PsiNamedElement> results = Lists.newArrayList();
		for(PyExpression expression : expressions)
		{
			if(expression instanceof PsiNamedElement)
			{
				results.add((PsiNamedElement) expression);
			}
		}
		return results;
	}

	@Nullable
	public PsiNamedElement getNamedElement(@Nonnull final String the_name)
	{
		// Requires switching from stubs to AST in getTarget()
		return PyUtil.IterHelper.findName(getNamedElements(), the_name);
	}
}
