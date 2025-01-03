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

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

public class PyListLiteralExpressionImpl extends PySequenceExpressionImpl implements PyListLiteralExpression
{
	public PyListLiteralExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyListLiteralExpression(this);
	}

	public PsiElement add(@Nonnull PsiElement psiElement) throws IncorrectOperationException
	{
		checkPyExpression(psiElement);
		PyExpression element = (PyExpression) psiElement;
		PyExpression[] els = getElements();
		PyExpression lastArg = els.length == 0 ? null : els[els.length - 1];
		return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, lastArg, element);
	}

	private static void checkPyExpression(PsiElement psiElement) throws IncorrectOperationException
	{
		if(!(psiElement instanceof PyExpression))
		{
			throw new IncorrectOperationException("Element must be PyExpression: " + psiElement);
		}
	}

	public PsiElement addAfter(@Nonnull PsiElement psiElement, PsiElement afterThis) throws IncorrectOperationException
	{
		checkPyExpression(psiElement);
		checkPyExpression(afterThis);
		return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, (PyExpression) afterThis, (PyExpression) psiElement);
	}

	public PsiElement addBefore(@Nonnull PsiElement psiElement, PsiElement beforeThis) throws IncorrectOperationException
	{
		checkPyExpression(psiElement);
		return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, null, (PyExpression) psiElement);
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		return PyBuiltinCache.getInstance(this).createLiteralCollectionType(this, "list", context);
	}
}
