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
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyElementTypes;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.PyClass;
import org.consulo.python.psi.PyElementGenerator;
import org.consulo.python.psi.PyElementVisitor;
import org.consulo.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyClassImpl extends PyElementImpl
		implements PyClass {
	public PyClassImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	public PsiElement setName(String name) throws IncorrectOperationException {
		ASTNode nameElement = PyElementGenerator.getInstance().createNameIdentifier(getProject(), name);
		getNode().replaceChild(findNameIdentifier(), nameElement);
		return this;
	}

	@Override
	@Nullable
	public String getName() {
		ASTNode node = findNameIdentifier();
		return node != null ? node.getText() : null;
	}

	private ASTNode findNameIdentifier() {
		return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
	}
	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyClass(this);
	}

	@Override
	@NotNull
	public PyStatementList getStatementList() {
		return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		return processor.execute(this, state);
	}

	@Override
	public int getTextOffset() {
		ASTNode name = findNameIdentifier();
		return name != null ? name.getStartOffset() : super.getTextOffset();
	}
}