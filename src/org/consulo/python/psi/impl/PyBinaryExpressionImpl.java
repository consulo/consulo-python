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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyBinaryExpressionImpl extends PyElementImpl
		implements PyBinaryExpression {
	private static final TokenSet BINARY_OPS = TokenSet.create(PyTokenTypes.OR_KEYWORD, PyTokenTypes.AND_KEYWORD, PyTokenTypes.LT, PyTokenTypes.GT, PyTokenTypes.OR, PyTokenTypes.XOR, 
			PyTokenTypes.AND, 
	PyTokenTypes.LTLT, PyTokenTypes.GTGT, PyTokenTypes.EQEQ, PyTokenTypes.GE, PyTokenTypes.LE, PyTokenTypes.NE, PyTokenTypes.NE_OLD, PyTokenTypes.IN_KEYWORD, PyTokenTypes.IS_KEYWORD, PyTokenTypes.NOT_KEYWORD, PyTokenTypes.PLUS, PyTokenTypes.MINUS, PyTokenTypes.MULT, PyTokenTypes.FLOORDIV, PyTokenTypes.DIV, PyTokenTypes.PERC);

	public PyBinaryExpressionImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyBinaryExpression(this);
	}

	@Override
	@PsiCached
	public PyExpression getLeftExpression() {
		return PsiTreeUtil.getChildOfType(this, PyExpression.class);
	}

	@Override
	@PsiCached
	public PyExpression getRightExpression() {
		return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
	}

	@Override
	@PsiCached
	public List<PyElementType> getOperator() {
		List list = new ArrayList(3);
		ASTNode child = getNode().getFirstChildNode();
		while (child != null) {
			IElementType elType = child.getElementType();
			if (((elType instanceof PyElementTypeImpl)) && (BINARY_OPS.contains(elType))) {
				list.add(elType);
			}
			child = child.getTreeNext();
		}
		return list;
	}

	@Override
	@PsiCached
	public boolean isOperator(String chars) {
		ASTNode child = getNode().getFirstChildNode();
		StringBuffer buf = new StringBuffer();
		while (child != null) {
			IElementType elType = child.getElementType();
			if (((elType instanceof PyElementTypeImpl)) && (BINARY_OPS.contains(elType))) {
				buf.append(child.getText());
			}
			child = child.getTreeNext();
		}
		return buf.toString().equals(chars);
	}

	@Override
	public PyExpression getOppositeExpression(PyExpression expression) throws IllegalArgumentException {
		PyExpression right = getRightExpression();
		PyExpression left = getLeftExpression();
		if (expression.equals(left)) {
			return right;
		}
		if (expression.equals(right)) {
			return left;
		}
		throw new IllegalArgumentException("expression " + expression + " is neither left exp or right exp");
	}

	@Override
	protected void deletePyChild(PyElementImpl element)
			throws IncorrectOperationException {
		PyExpression left = getLeftExpression();
		PyExpression right = getRightExpression();
		if (left == element)
			replace(right);
		else if (right == element)
			replace(left);
		else
			throw new IncorrectOperationException("Element " + element + " is neither left expression or right expression");
	}

	@Override
	@Nullable
	protected Class<? extends PsiElement> getValidChildClass() {
		return PyElement.class;
	}
}