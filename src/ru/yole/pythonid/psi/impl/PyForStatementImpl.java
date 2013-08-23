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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyForStatement;
import ru.yole.pythonid.psi.PyStatementList;

public class PyForStatementImpl extends PyElementImpl
		implements PyForStatement {
	public PyForStatementImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyForStatement(this);
	}

	@NotNull
	public PyStatementList getStatementList() {
		PyStatementList tmp15_12 = ((PyStatementList) childToPsiNotNull(getPyElementTypes().STATEMENT_LISTS, 0));
		if (tmp15_12 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp15_12;
	}

	@Nullable
	public PyStatementList getElseStatementList() {
		PyStatementList result = (PyStatementList) childToPsi(getPyElementTypes().STATEMENT_LISTS, 1);

		return result;
	}

	@Nullable
	public PyExpression getTargetExpression() {
		return (PyExpression) childToPsi(getPyElementTypes().EXPRESSIONS, 0);
	}

	@Nullable
	public PyExpression getLoopExpression() {
		return (PyExpression) childToPsi(getPyElementTypes().EXPRESSIONS, 1);
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		if (!getStatementList().processDeclarations(processor, substitutor, null, place)) {
			return false;
		}
		PyStatementList elseList = getElseStatementList();
		if (elseList != null) {
			return elseList.processDeclarations(processor, substitutor, null, place);
		}
		return true;
	}
}