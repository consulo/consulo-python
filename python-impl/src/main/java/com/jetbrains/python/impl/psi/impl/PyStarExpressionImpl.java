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
import jakarta.annotation.Nullable;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 27.02.2010
 * Time: 14:27:28
 */
public class PyStarExpressionImpl extends PyElementImpl implements PyStarExpression
{
	public PyStarExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Nullable
	public PyExpression getExpression()
	{
		return PsiTreeUtil.getChildOfType(this, PyExpression.class);
	}

	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		return null;
	}

	public void acceptPyVisitor(PyElementVisitor visitor)
	{
		visitor.visitPyStarExpression(this);
	}

	public boolean isAssignmentTarget()
	{
		return getExpression() instanceof PyTargetExpression;
	}

	public boolean isUnpacking()
	{
		if(isAssignmentTarget())
		{
			return false;
		}
		PsiElement parent = getParent();
		while(parent instanceof PyParenthesizedExpression)
		{
			parent = parent.getParent();
		}
		return parent instanceof PyTupleExpression || parent instanceof PyListLiteralExpression || parent instanceof PySetLiteralExpression;
	}
}
