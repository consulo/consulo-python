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
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.*;

public class PyAssignmentStatementImpl extends PyElementImpl
		implements PyAssignmentStatement {
	public PyAssignmentStatementImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyAssignmentStatement(this);
	}

	@PsiCached
	public PyExpression[] getTargets() {
		ASTNode[] nodes = getNode().getChildren(getPyElementTypes().EXPRESSIONS);
		ASTNode[] targets = new ASTNode[nodes.length - 1];
		System.arraycopy(nodes, 0, targets, 0, nodes.length - 1);
		return (PyExpression[]) nodesToPsi(targets, PyExpression.EMPTY_ARRAY);
	}

	@PsiCached
	@Nullable
	public PyExpression getAssignedValue() {
		PsiElement child = getLastChild();
		while ((child != null) && (!(child instanceof PyExpression))) {
			child = child.getPrevSibling();
		}
		return (PyExpression) child;
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		if ((PsiTreeUtil.getParentOfType(this, PyFunction.class) == null) && (PsiTreeUtil.getParentOfType(place, PyFunction.class) != null)) {
			if (PsiTreeUtil.getParentOfType(this, PyClass.class) != null) {
				return true;
			}
			if (PsiTreeUtil.getParentOfType(place, PyGlobalStatement.class) == null) {
				return true;
			}
		}

		for (PyExpression expression : getTargets()) {
			if (!expression.processDeclarations(processor, substitutor, lastParent, place)) {
				return false;
			}
		}
		return true;
	}
}