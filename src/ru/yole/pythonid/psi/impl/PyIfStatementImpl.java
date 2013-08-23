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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PyElementTypes;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyIfStatement;
import ru.yole.pythonid.psi.PyStatementList;

public class PyIfStatementImpl extends PyElementImpl
		implements PyIfStatement {
	private static final Logger LOG = Logger.getInstance("#ru.yole.pythonid.psi.impl.PyIfStatementImpl");

	public PyIfStatementImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyIfStatement(this);
	}

	@NotNull
	public PyExpression[] getConditions() {
		PyExpression[] tmp17_14 = ((PyExpression[]) childrenToPsi(getPyElementTypes().EXPRESSIONS, PyExpression.EMPTY_ARRAY));
		if (tmp17_14 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp17_14;
	}

	@NotNull
	public PyStatementList[] getStatementLists() {
		PyElementTypes eltypes = getLanguage().getElementTypes();
		ASTNode[] conditions = getNode().getChildren(eltypes.EXPRESSIONS);
		PyStatementList[] statementLists = (PyStatementList[]) childrenToPsi(eltypes.STATEMENT_LISTS, PyStatementList.EMPTY_ARRAY);
		LOG.assertTrue((statementLists.length == conditions.length) || (statementLists.length == conditions.length + 1));
		if (statementLists.length > conditions.length) {
			PyStatementList[] result = new PyStatementList[conditions.length];
			System.arraycopy(statementLists, 0, result, 0, conditions.length);
			PyStatementList[] tmp91_89 = result;
			if (tmp91_89 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp91_89;
		}
		PyStatementList[] tmp107_106 = statementLists;
		if (tmp107_106 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp107_106;
	}

	@Nullable
	public PyStatementList getElseStatementList() {
		ASTNode[] conditions = getNode().getChildren(getPyElementTypes().EXPRESSIONS);
		PyStatementList[] statementLists = (PyStatementList[]) childrenToPsi(getPyElementTypes().STATEMENT_LISTS, PyStatementList.EMPTY_ARRAY);
		LOG.assertTrue((statementLists.length == conditions.length) || (statementLists.length == conditions.length + 1));
		if (statementLists.length > conditions.length) {
			return statementLists[(statementLists.length - 1)];
		}
		return null;
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		PyStatementList[] statementLists = getStatementLists();
		for (PyStatementList statementList : statementLists) {
			if (!statementList.processDeclarations(processor, substitutor, lastParent, place)) {
				return false;
			}
		}
		PyStatementList elseList = getElseStatementList();

		if ((elseList != null) && (!elseList.processDeclarations(processor, substitutor, lastParent, place))) {
			return false;
		}
		return true;
	}
}