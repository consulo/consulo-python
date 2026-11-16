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

import com.jetbrains.python.impl.PyStubElementTypes;
import java.util.List;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;
import com.google.common.collect.Lists;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
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
		super(stub, PyStubElementTypes.EXCEPT_PART);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyExceptBlock(this);
	}

	@Nullable
    @Override
    @RequiredReadAction
	public PyExpression getExceptClass()
	{
		return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
	}

	@Nullable
    @Override
    @RequiredReadAction
	public PyExpression getTarget()
	{
		return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
	}

    @Override
    @RequiredReadAction
	public PyStatementList getStatementList()
	{
		return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
	}

	@Override
    @RequiredReadAction
    public List<PsiNamedElement> getNamedElements()
	{
		List<PyExpression> expressions = PyUtil.flattenedParensAndStars(getTarget());
		List<PsiNamedElement> results = Lists.newArrayList();
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
    @RequiredReadAction
	public PsiNamedElement getNamedElement(String the_name)
	{
		// Requires switching from stubs to AST in getTarget()
		return PyUtil.IterHelper.findName(getNamedElements(), the_name);
	}
}
