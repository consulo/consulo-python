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
import org.consulo.python.PyElementTypes;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyForStatement;
import org.consulo.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyForStatementImpl extends PyElementImpl
		implements PyForStatement {
	public PyForStatementImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyForStatement(this);
	}

	@Override
	@NotNull
	public PyStatementList getStatementList() {
		PyStatementList tmp15_12 = childToPsiNotNull(PyElementTypes.STATEMENT_LISTS, 0);
		return tmp15_12;
	}

	@Override
	@Nullable
	public PyStatementList getElseStatementList() {
		PyStatementList result = childToPsi(PyElementTypes.STATEMENT_LISTS, 1);

		return result;
	}

	@Override
	@Nullable
	public PyExpression getTargetExpression() {
		return childToPsi(PyElementTypes.EXPRESSIONS, 0);
	}

	@Override
	@Nullable
	public PyExpression getLoopExpression() {
		return childToPsi(PyElementTypes.EXPRESSIONS, 1);
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		if (!getStatementList().processDeclarations(processor, state, null, place)) {
			return false;
		}
		PyStatementList elseList = getElseStatementList();
		if (elseList != null) {
			return elseList.processDeclarations(processor, state, null, place);
		}
		return true;
	}
}