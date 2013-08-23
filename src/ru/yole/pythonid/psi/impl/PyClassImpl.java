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
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PyClass;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyStatementList;

import javax.swing.*;

public class PyClassImpl extends PyElementImpl
		implements PyClass {
	public PyClassImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	public PsiElement setName(String name) throws IncorrectOperationException {
		ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
		getNode().replaceChild(findNameIdentifier(), nameElement);
		return this;
	}

	@Nullable
	public String getName() {
		ASTNode node = findNameIdentifier();
		return node != null ? node.getText() : null;
	}

	private ASTNode findNameIdentifier() {
		return getNode().findChildByType(getLanguage().getTokenTypes().IDENTIFIER);
	}

	public Icon getIcon(int flags) {
		return Icons.CLASS_ICON;
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyClass(this);
	}

	@NotNull
	public PyStatementList getStatementList() {
		PyStatementList tmp17_14 = ((PyStatementList) childToPsiNotNull(getLanguage().getElementTypes().STATEMENT_LIST));
		if (tmp17_14 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp17_14;
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		return processor.execute(this, substitutor);
	}

	public int getTextOffset() {
		ASTNode name = findNameIdentifier();
		return name != null ? name.getStartOffset() : super.getTextOffset();
	}
}