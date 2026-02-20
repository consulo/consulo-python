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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author yole
 */
public class PyTupleExpressionImpl extends PySequenceExpressionImpl implements PyTupleExpression
{
	public PyTupleExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyTupleExpression(this);
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		return PyTupleType.create(this, ContainerUtil.map(getElements(), context::getType));
	}

	public Iterator<PyExpression> iterator()
	{
		return Arrays.asList(getElements()).iterator();
	}

	@Override
	public void deleteChildInternal(@Nonnull ASTNode child)
	{
		super.deleteChildInternal(child);
		PyExpression[] children = getElements();
		PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
		if(children.length == 1 && PyPsiUtils.getNextComma(children[0]) == null)
		{
			addAfter(generator.createComma().getPsi(), children[0]);
		}
		else if(children.length == 0 && !(getParent() instanceof PyParenthesizedExpression))
		{
			replace(generator.createExpressionFromText(LanguageLevel.forElement(this), "()"));
		}
	}
}
