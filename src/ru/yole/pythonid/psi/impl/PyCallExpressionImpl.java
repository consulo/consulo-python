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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.*;

public class PyCallExpressionImpl extends PyElementImpl
		implements PyCallExpression {
	public PyCallExpressionImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyCallExpression(this);
	}

	@PsiCached
	public PyReferenceExpression getCalledFunctionReference() {
		return (PyReferenceExpression) PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
	}

	@PsiCached
	public PyArgumentList getArgumentList() {
		return (PyArgumentList) PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
	}

	public void addArgument(PyExpression expression) {
		PyExpression[] arguments = getArgumentList().getArguments();
		try {
			getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, arguments.length == 0 ? null : arguments[(arguments.length - 1)], expression);
		} catch (IncorrectOperationException e1) {
			throw new IllegalArgumentException(e1);
		}
	}

	public String toString() {
		return "PyCallExpression: " + getCalledFunctionReference().getReferencedName();
	}
}