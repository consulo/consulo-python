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

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import consulo.language.ast.TokenSet;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;

/**
 * @author yole
 */
public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement
{
	private static final TokenSet WITH_ITEM = TokenSet.create(PyElementTypes.WITH_ITEM);

	public PyWithStatementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	protected void acceptPyVisitor(final PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyWithStatement(this);
	}

	@Nonnull
	public List<PsiNamedElement> getNamedElements()
	{
		PyWithItem[] items = PsiTreeUtil.getChildrenOfType(this, PyWithItem.class);
		List<PsiNamedElement> result = new ArrayList<>();
		if(items != null)
		{
			for(PyWithItem item : items)
			{
				PyExpression targetExpression = item.getTarget();
				final List<PyExpression> expressions = PyUtil.flattenedParensAndTuples(targetExpression);
				for(PyExpression expression : expressions)
				{
					if(expression instanceof PsiNamedElement)
					{
						result.add((PsiNamedElement) expression);
					}
				}
			}
		}
		return result;
	}

	@Nullable
	public PsiNamedElement getNamedElement(@Nonnull final String the_name)
	{
		return PyUtil.IterHelper.findName(getNamedElements(), the_name);
	}

	public PyWithItem[] getWithItems()
	{
		return childrenToPsi(WITH_ITEM, PyWithItem.EMPTY_ARRAY);
	}

	@Override
	@Nonnull
	public PyStatementList getStatementList()
	{
		final PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
		assert statementList != null : "Statement list missing for with statement " + getText();
		return statementList;
	}
}
