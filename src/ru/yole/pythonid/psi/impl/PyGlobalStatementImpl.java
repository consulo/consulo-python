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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExpression;
import ru.yole.pythonid.psi.PyGlobalStatement;
import ru.yole.pythonid.psi.PyReferenceExpression;

public class PyGlobalStatementImpl extends PyElementImpl
		implements PyGlobalStatement {
	private TokenSet REFERENCES = TokenSet.create(new IElementType[]{getPyElementTypes().REFERENCE_EXPRESSION});

	public PyGlobalStatementImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyGlobalStatement(this);
	}

	@NotNull
	public PyReferenceExpression[] getGlobals() {
		PyReferenceExpression[] tmp14_11 = ((PyReferenceExpression[]) childrenToPsi(this.REFERENCES, PyReferenceExpression.EMPTY_ARRAY));
		if (tmp14_11 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp14_11;
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		for (PyExpression expression : getGlobals()) {
			if (!expression.processDeclarations(processor, substitutor, lastParent, place)) {
				return false;
			}
		}
		return true;
	}
}