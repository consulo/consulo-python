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
import com.intellij.psi.util.PsiTreeUtil;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.PsiCached;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyKeywordArgument;
import org.jetbrains.annotations.Nullable;

public class PyKeywordArgumentImpl extends PyElementImpl
		implements PyKeywordArgument {
	public PyKeywordArgumentImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	@PsiCached
	@Nullable
	public String getKeyword() {
		ASTNode node = getKeywordNode();
		return node != null ? node.getText() : null;
	}

	@Override
	@PsiCached
	public ASTNode getKeywordNode() {
		return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
	}

	@Override
	@PsiCached
	public PyExpression getValueExpression() {
		return PsiTreeUtil.getChildOfType(this, PyExpression.class);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ": " + getKeyword();
	}

	@Override
	@Nullable
	protected Class<? extends PsiElement> getValidChildClass() {
		return PyExpression.class;
	}
}