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
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiNamedElement;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PyGlobalStatementImpl extends PyElementImpl implements PyGlobalStatement
{
	private static final TokenSet TARGET_EXPRESSION_SET = TokenSet.create(PyElementTypes.TARGET_EXPRESSION);

	public PyGlobalStatementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyGlobalStatement(this);
	}

	@Nonnull
	public PyTargetExpression[] getGlobals()
	{
		return childrenToPsi(TARGET_EXPRESSION_SET, PyTargetExpression.EMPTY_ARRAY);
	}

	@Nonnull
	public List<PsiNamedElement> getNamedElements()
	{
		return Arrays.<PsiNamedElement>asList(getGlobals());
	}

	@Nullable
	public PsiNamedElement getNamedElement(@Nonnull final String the_name)
	{
		return PyUtil.IterHelper.findName(getNamedElements(), the_name);
	}

	public void addGlobal(final String name)
	{
		final PyElementGenerator pyElementGenerator = PyElementGenerator.getInstance(getProject());
		add(pyElementGenerator.createComma().getPsi());
		add(pyElementGenerator.createFromText(LanguageLevel.getDefault(), PyGlobalStatement.class, "global " + name).getGlobals()[0]);
	}

	@Override
	public void deleteChildInternal(@Nonnull ASTNode child)
	{
		if(ArrayUtil.contains(child.getPsi(), getGlobals()))
		{
			PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
		}
		super.deleteChildInternal(child);
	}
}
