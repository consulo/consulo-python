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

package ru.yole.pythonid.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PyElementTypes;
import ru.yole.pythonid.PyTokenTypes;
import ru.yole.pythonid.psi.PsiCached;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyListCompExpression;

import java.util.ArrayList;
import java.util.List;

public class PyListCompExpressionImpl extends PyElementImpl
		implements PyListCompExpression {
	public PyListCompExpressionImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyListCompExpression(this);
	}

	@PsiCached
	public PyExpression getResultExpression() {
		ASTNode[] exprs = getNode().getChildren(getLanguage().getElementTypes().EXPRESSIONS);

		return exprs.length == 0 ? null : (PyExpression) exprs[0].getPsi();
	}

	@PsiCached
	public List<PyListCompExpression.ListCompComponent> getComponents() {
		ASTNode node = getNode().getFirstChildNode();
		PyTokenTypes tokenTypes = getLanguage().getTokenTypes();
		List list = new ArrayList(5);
		while (node != null) {
			IElementType type = node.getElementType();
			ASTNode next = getNextExpression(node);
			if (next == null) break;
			if (type == tokenTypes.IF_KEYWORD) {
				final PyExpression test = (PyExpression) next.getPsi();
				list.add(new PyListCompExpression.IfComponent() {
					public PyExpression getTest() {
						return test;
					}
				});
			} else if (type == tokenTypes.FOR_KEYWORD) {
				ASTNode next2 = getNextExpression(next);
				if (next2 == null) break;
				final PyExpression variable = (PyExpression) next.getPsi();
				final PyExpression iterated = (PyExpression) next2.getPsi();
				list.add(new PyListCompExpression.ForComponent() {
					public PyExpression getIteratorVariable() {
						return variable;
					}

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
		PyElementTypes elementTypes = getLanguage().getElementTypes();
		do {
			node = node.getTreeNext();
		}
		while ((node != null) && (!elementTypes.EXPRESSIONS.contains(node.getElementType())));
		return node;
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		for (PyListCompExpression.ListCompComponent component : getComponents()) {
			if ((component instanceof PyListCompExpression.ForComponent)) {
				PyListCompExpression.ForComponent forComponent = (PyListCompExpression.ForComponent) component;
				if (!forComponent.getIteratorVariable().processDeclarations(processor, substitutor, null, place))
					return false;
			}
		}
		return true;
	}
}