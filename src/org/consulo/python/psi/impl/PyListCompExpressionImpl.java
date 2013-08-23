/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package org.consulo.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import org.consulo.python.PyElementTypes;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.PsiCached;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyListCompExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PyListCompExpressionImpl extends PyElementImpl
		implements PyListCompExpression {
	public PyListCompExpressionImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyListCompExpression(this);
	}

	@Override
	@PsiCached
	public PyExpression getResultExpression() {
		ASTNode[] exprs = getNode().getChildren(PyElementTypes.EXPRESSIONS);

		return exprs.length == 0 ? null : (PyExpression) exprs[0].getPsi();
	}

	@Override
	@PsiCached
	public List<PyListCompExpression.ListCompComponent> getComponents() {
		ASTNode node = getNode().getFirstChildNode();
		List list = new ArrayList(5);
		while (node != null) {
			IElementType type = node.getElementType();
			ASTNode next = getNextExpression(node);
			if (next == null) break;
			if (type == PyTokenTypes.IF_KEYWORD) {
				final PyExpression test = (PyExpression) next.getPsi();
				list.add(new PyListCompExpression.IfComponent() {
					@Override
					public PyExpression getTest() {
						return test;
					}
				});
			} else if (type == PyTokenTypes.FOR_KEYWORD) {
				ASTNode next2 = getNextExpression(next);
				if (next2 == null) break;
				final PyExpression variable = (PyExpression) next.getPsi();
				final PyExpression iterated = (PyExpression) next2.getPsi();
				list.add(new PyListCompExpression.ForComponent() {
					@Override
					public PyExpression getIteratorVariable() {
						return variable;
					}

					@Override
					public PyExpression getIteratedList() {
						return iterated;
					}
				});
			}
			node = node.getTreeNext();
		}
		return list;
	}

	private ASTNode getNextExpression(ASTNode after) {
		ASTNode node = after;
		do {
			node = node.getTreeNext();
		}
		while ((node != null) && (!PyElementTypes.EXPRESSIONS.contains(node.getElementType())));
		return node;
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		for (PyListCompExpression.ListCompComponent component : getComponents()) {
			if ((component instanceof PyListCompExpression.ForComponent)) {
				PyListCompExpression.ForComponent forComponent = (PyListCompExpression.ForComponent) component;
				if (!forComponent.getIteratorVariable().processDeclarations(processor, state, null, place))
					return false;
			}
		}
		return true;
	}
}