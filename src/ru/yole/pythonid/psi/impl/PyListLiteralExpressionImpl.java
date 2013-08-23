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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.*;

public class PyListLiteralExpressionImpl extends PyElementImpl
		implements PyListLiteralExpression {
	public PyListLiteralExpressionImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyListLiteralExpression(this);
	}

	@PsiCached
	@NotNull
	public PyExpression[] getElements() {
		PyExpression[] tmp17_14 = ((PyExpression[]) childrenToPsi(getPyElementTypes().EXPRESSIONS, PyExpression.EMPTY_ARRAY));
		if (tmp17_14 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp17_14;
	}

	public PsiElement add(PsiElement psiElement)
			throws IncorrectOperationException {
		PyUtil.ensureWritable(this);
		checkPyExpression(psiElement);
		PyExpression element = (PyExpression) psiElement;
		PyExpression[] els = getElements();
		PyExpression lastArg = els.length == 0 ? null : els[(els.length - 1)];
		return getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, lastArg, element);
	}

	private void checkPyExpression(PsiElement psiElement)
			throws IncorrectOperationException {
		if (!(psiElement instanceof PyExpression))
			throw new IncorrectOperationException("Element must be PyExpression: " + psiElement);
	}

	public PsiElement addAfter(PsiElement psiElement, PsiElement afterThis)
			throws IncorrectOperationException {
		PyUtil.ensureWritable(this);
		checkPyExpression(psiElement);
		checkPyExpression(afterThis);
		return getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, (PyExpression) afterThis, (PyExpression) psiElement);
	}

	public PsiElement addBefore(PsiElement psiElement, PsiElement beforeThis)
			throws IncorrectOperationException {
		PyUtil.ensureWritable(this);
		checkPyExpression(psiElement);
		return getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, null, (PyExpression) psiElement);
	}

	protected void deletePyChild(PyElementImpl element)
			throws IncorrectOperationException {
		PyUtil.ensureWritable(this);
		if ((element instanceof PyExpression)) {
			PyExpression expression = (PyExpression) element;
			ASTNode node = getNode();
			ASTNode exprNode = expression.getNode();
			ASTNode next = getNextComma(exprNode);
			ASTNode prev = getPrevComma(exprNode);
			node.removeChild(exprNode);
			if (next != null)
				node.removeChild(next);
			else if (prev != null)
				node.removeChild(prev);
		} else {
			super.deletePyChild(element);
		}
	}

	@Nullable
	protected Class<? extends PsiElement> getValidChildClass() {
		return PyExpression.class;
	}

}