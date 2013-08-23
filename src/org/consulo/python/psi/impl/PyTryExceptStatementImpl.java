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
import com.intellij.psi.tree.TokenSet;
import org.consulo.python.PyElementTypes;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyExceptBlock;
import org.consulo.python.psi.PyStatementList;
import org.consulo.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTryExceptStatementImpl extends PyElementImpl implements PyTryExceptStatement {
	private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_BLOCK);

	public PyTryExceptStatementImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyTryExceptStatement(this);
	}

	@Override
	@NotNull
	public PyStatementList getTryStatementList() {
		return childToPsiNotNull(PyElementTypes.STATEMENT_LISTS, 0);
	}

	@Override
	@NotNull
	public PyExceptBlock[] getExceptBlocks() {
		return childrenToPsi(EXCEPT_BLOCKS, PyExceptBlock.EMPTY_ARRAY);
	}

	@Override
	@Nullable
	public PyStatementList getElseStatementList() {
		return childToPsi(PyElementTypes.STATEMENT_LISTS, 1);
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		if (lastParent != null) {
			return true;
		}

		if (!getTryStatementList().processDeclarations(processor, state, null, place)) {
			return false;
		}

		for (PyExceptBlock block : getExceptBlocks()) {
			if (!block.processDeclarations(processor, state, null, place)) {
				return false;
			}
		}

		PyStatementList elseStatementList = getElseStatementList();
		if (elseStatementList != null) {
			return elseStatementList.processDeclarations(processor, state, null, place);
		}
		return true;
	}

}