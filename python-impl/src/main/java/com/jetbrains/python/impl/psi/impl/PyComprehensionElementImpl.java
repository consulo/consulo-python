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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.collect.Lists;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyComprehensionComponent;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyComprehensionIfComponent;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.psi.PyUtil;

/**
 * Comprehension-like element base, for list comps ang generators.
 * User: dcheryasov
 * Date: Jul 31, 2008
 */
public abstract class PyComprehensionElementImpl extends PyElementImpl implements PyComprehensionElement
{
	public PyComprehensionElementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	/**
	 * In "[x+1 for x in (1,2,3)]" result expression is "x+1".
	 *
	 * @return result expression.
	 */
	@Nullable
	public PyExpression getResultExpression()
	{
		ASTNode[] exprs = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
		return exprs.length == 0 ? null : (PyExpression) exprs[0].getPsi();
	}

	/**
	 * In "[x+1 for x in (1,2,3)]" a "for component" is "x".
	 *
	 * @return all "for components"
	 */
	public List<PyComprehensionForComponent> getForComponents()
	{
		final List<PyComprehensionForComponent> list = new ArrayList<>(5);
		visitComponents(new ComprehensionElementVisitor()
		{
			@Override
			void visitForComponent(PyComprehensionForComponent component)
			{
				list.add(component);
			}
		});
		return list;
	}

	private void visitComponents(ComprehensionElementVisitor visitor)
	{
		ASTNode node = getNode().getFirstChildNode();
		while(node != null)
		{
			IElementType type = node.getElementType();
			ASTNode next = getNextExpression(node);
			if(next == null)
			{
				break;
			}
			if(type == PyTokenTypes.FOR_KEYWORD)
			{
				ASTNode next2 = getNextExpression(next);
				if(next2 == null)
				{
					break;
				}
				final PyExpression variable = (PyExpression) next.getPsi();
				final PyExpression iterated = (PyExpression) next2.getPsi();
				final boolean isAsync = Optional.ofNullable(node.getTreePrev()).map(ASTNode::getTreePrev).map(asyncNode -> asyncNode.getElementType() == PyTokenTypes.ASYNC_KEYWORD).orElse(false);

				visitor.visitForComponent(new PyComprehensionForComponent()
				{
					public PyExpression getIteratorVariable()
					{
						return variable;
					}

					public PyExpression getIteratedList()
					{
						return iterated;
					}

					@Override
					public boolean isAsync()
					{
						return isAsync;
					}
				});
			}
			else if(type == PyTokenTypes.IF_KEYWORD)
			{
				final PyExpression test = (PyExpression) next.getPsi();
				visitor.visitIfComponent(new PyComprehensionIfComponent()
				{
					public PyExpression getTest()
					{
						return test;
					}
				});
			}
			node = node.getTreeNext();
		}
	}

	public List<PyComprehensionIfComponent> getIfComponents()
	{
		final List<PyComprehensionIfComponent> list = new ArrayList<>(5);
		visitComponents(new ComprehensionElementVisitor()
		{
			@Override
			void visitIfComponent(PyComprehensionIfComponent component)
			{
				list.add(component);
			}
		});
		return list;
	}

	public List<PyComprehensionComponent> getComponents()
	{
		final List<PyComprehensionComponent> list = new ArrayList<>(5);
		visitComponents(new ComprehensionElementVisitor()
		{
			@Override
			void visitForComponent(PyComprehensionForComponent component)
			{
				list.add(component);
			}

			@Override
			void visitIfComponent(PyComprehensionIfComponent component)
			{
				list.add(component);
			}
		});
		return list;
	}

	@Nullable
	private static ASTNode getNextExpression(ASTNode after)
	{
		ASTNode node = after;
		do
		{
			node = node.getTreeNext();
		}
		while(node != null && !PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens().contains(node.getElementType()));
		return node;
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
		final List<PyExpression> expressions = PyUtil.flattenedParensAndLists(for_targets);
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

	abstract class ComprehensionElementVisitor
	{
		void visitIfComponent(PyComprehensionIfComponent component)
		{
		}

		void visitForComponent(PyComprehensionForComponent component)
		{
		}
	}
}
