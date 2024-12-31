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

import java.util.Arrays;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.collect.Lists;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyGeneratorExpressionImpl extends PyComprehensionElementImpl implements PyGeneratorExpression
{
	public PyGeneratorExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyGeneratorExpression(this);
	}

	@Nullable
	@Override
	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		final PyExpression resultExpr = getResultExpression();
		final PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
		final PyClass generator = cache.getClass(PyNames.FAKE_GENERATOR);
		if(resultExpr != null && generator != null)
		{
			final List<PyType> parameters = Arrays.asList(context.getType(resultExpr), null, PyNoneType.INSTANCE);
			return new PyCollectionTypeImpl(generator, false, parameters);
		}
		return null;
	}

	@Nonnull
	public List<PsiNamedElement> getNamedElements()
	{
		// extract whatever names are defined in "for" components
		List<PyComprehensionForComponent> fors = getForComponents();
		PyExpression[] for_targets = new PyExpression[fors.size()];
		int i = 0;
		for(PyComprehensionForComponent for_comp : fors)
		{
			for_targets[i] = for_comp.getIteratorVariable();
			i += 1;
		}
		final List<PyExpression> expressions = PyUtil.flattenedParensAndStars(for_targets);
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
		return PyUtil.IterHelper.findName(getNamedElements(), the_name);
	}
}
