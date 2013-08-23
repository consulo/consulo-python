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
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyExceptBlock;
import ru.yole.pythonid.psi.PyStatementList;
import ru.yole.pythonid.psi.PyTryExceptStatement;

public class PyTryExceptStatementImpl extends PyElementImpl
		implements PyTryExceptStatement {
	private TokenSet EXCEPT_BLOCKS;

	public PyTryExceptStatementImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
		this.EXCEPT_BLOCKS = TokenSet.create(new IElementType[]{language.getElementTypes().EXCEPT_BLOCK});
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyTryExceptStatement(this);
	}

	@NotNull
	public PyStatementList getTryStatementList() {
		PyStatementList tmp18_15 = ((PyStatementList) childToPsiNotNull(getLanguage().getElementTypes().STATEMENT_LISTS, 0));
		if (tmp18_15 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp18_15;
	}

	@NotNull
	public PyExceptBlock[] getExceptBlocks() {
		PyExceptBlock[] tmp14_11 = ((PyExceptBlock[]) childrenToPsi(this.EXCEPT_BLOCKS, PyExceptBlock.EMPTY_ARRAY));
		if (tmp14_11 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp14_11;
	}

	@Nullable
	public PyStatementList getElseStatementList() {
		return (PyStatementList) childToPsi(getLanguage().getElementTypes().STATEMENT_LISTS, 1);
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		if (!getTryStatementList().processDeclarations(processor, substitutor, null, place)) {
			return false;
		}

		for (PyExceptBlock block : getExceptBlocks()) {
			if (!block.processDeclarations(processor, substitutor, null, place)) {
				return false;
			}
		}

		PyStatementList elseStatementList = getElseStatementList();
		if (elseStatementList != null) {
			return elseStatementList.processDeclarations(processor, substitutor, null, place);
		}
		return true;
	}
}