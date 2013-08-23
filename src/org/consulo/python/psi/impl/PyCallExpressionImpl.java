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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.psi.*;


public class PyCallExpressionImpl extends PyElementImpl implements PyCallExpression {
	public PyCallExpressionImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyCallExpression(this);
	}

	@Override
	@PsiCached
	public PyReferenceExpression getCalledFunctionReference() {
		return PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
	}

	@Override
	@PsiCached
	public org.consulo.python.psi.PyArgumentList getArgumentList() {
		return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
	}

	@Override
	public void addArgument(PyExpression expression) {
		PyExpression[] arguments = getArgumentList().getArguments();
		try {
			PyElementGenerator.getInstance().insertItemIntoList(getProject(), this, arguments.length == 0 ? null : arguments[(arguments.length - 1)], expression);
		} catch (IncorrectOperationException e1) {
			throw new IllegalArgumentException(e1);
		}
	}

	@Override
	public String toString() {
		return "PyCallExpression: " + getCalledFunctionReference().getReferencedName();
	}
}