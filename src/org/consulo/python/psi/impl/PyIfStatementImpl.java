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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.consulo.python.PyElementTypes;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExpression;
import org.consulo.python.psi.PyIfStatement;
import org.consulo.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyIfStatementImpl extends PyElementImpl
		implements PyIfStatement {
	private static final Logger LOG = Logger.getInstance("#ru.yole.pythonid.psi.impl.PyIfStatementImpl");

	public PyIfStatementImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyIfStatement(this);
	}

	@Override
	@NotNull
	public PyExpression[] getConditions() {
		return childrenToPsi(PyElementTypes.EXPRESSIONS, PyExpression.EMPTY_ARRAY);
	}

	@Override
	@NotNull
	public PyStatementList[] getStatementLists() {
		ASTNode[] conditions = getNode().getChildren(PyElementTypes.EXPRESSIONS);
		PyStatementList[] statementLists = childrenToPsi(PyElementTypes.STATEMENT_LISTS, PyStatementList.EMPTY_ARRAY);
		LOG.assertTrue((statementLists.length == conditions.length) || (statementLists.length == conditions.length + 1));
		if (statementLists.length > conditions.length) {
			PyStatementList[] result = new PyStatementList[conditions.length];
			System.arraycopy(statementLists, 0, result, 0, conditions.length);
			return result;
		}
		return statementLists;
	}

	@Override
	@Nullable
	public PyStatementList getElseStatementList() {
		ASTNode[] conditions = getNode().getChildren(PyElementTypes.EXPRESSIONS);
		PyStatementList[] statementLists = childrenToPsi(PyElementTypes.STATEMENT_LISTS, PyStatementList.EMPTY_ARRAY);
		LOG.assertTrue((statementLists.length == conditions.length) || (statementLists.length == conditions.length + 1));
		if (statementLists.length > conditions.length) {
			return statementLists[(statementLists.length - 1)];
		}
		return null;
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		PyStatementList[] statementLists = getStatementLists();
		for (PyStatementList statementList : statementLists) {
			if (!statementList.processDeclarations(processor, state, lastParent, place)) {
				return false;
			}
		}
		PyStatementList elseList = getElseStatementList();

		if ((elseList != null) && (!elseList.processDeclarations(processor, state, lastParent, place))) {
			return false;
		}
		return true;
	}
}