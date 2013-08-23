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
import com.intellij.psi.util.PsiTreeUtil;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PsiCached;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyStatement;
import ru.yole.pythonid.psi.PyStatementList;

public class PyStatementListImpl extends PyElementImpl
		implements PyStatementList {
	public PyStatementListImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyStatementList(this);
	}

	@PsiCached
	public PyStatement[] getStatements() {
		return (PyStatement[]) childrenToPsi(getPyElementTypes().STATEMENTS, PyStatement.EMPTY_ARRAY);
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		PsiElement parent = getParent();
		if (((parent instanceof PyStatement)) && (lastParent == null) && (PsiTreeUtil.isAncestor(parent, place, true))) {
			return true;
		}

		PyStatement[] statements = getStatements();
		if (lastParent != null) {
			for (int i = 0; i < statements.length; i++) {
				if (statements[i] == lastParent) {
					for (int j = i - 1; j >= 0; j--) {
						if (!statements[j].processDeclarations(processor, substitutor, lastParent, place)) {
							return false;
						}
					}
					return true;
				}
			}
		}

		for (PyStatement statement : getStatements()) {
			if (!statement.processDeclarations(processor, substitutor, lastParent, place)) {
				return false;
			}
		}
		return true;
	}
}