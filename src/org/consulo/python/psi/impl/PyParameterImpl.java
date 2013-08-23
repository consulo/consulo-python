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
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyElementTypes;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.PyElementGenerator;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyParameter;
import org.jetbrains.annotations.Nullable;

public class PyParameterImpl extends PyElementImpl
		implements PyParameter {
	public PyParameterImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	@Nullable
	public String getName() {
		ASTNode node = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
		return node != null ? node.getText() : null;
	}

	@Override
	public PsiElement setName(String name) throws IncorrectOperationException {
		ASTNode nameElement = PyElementGenerator.getInstance().createNameIdentifier(getProject(), name);
		getNode().replaceChild(getNode().getFirstChildNode(), nameElement);
		return this;
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyParameter(this);
	}

	@Override
	public boolean isPositionalContainer() {
		return getNode().findChildByType(PyTokenTypes.MULT) != null;
	}

	@Override
	public boolean isKeywordContainer() {
		return getNode().findChildByType(PyTokenTypes.EXP) != null;
	}

	@Override
	@Nullable
	public PyExpression getDefaultValue() {
		ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
		if (nodes.length > 0) {
			return (PyExpression) nodes[0].getPsi();
		}
		return null;
	}
}