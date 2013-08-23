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

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.PyElement;
import org.consulo.python.psi.PyElementEx;
import org.consulo.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;

public class PyElementImpl extends ASTWrapperPsiElement
		implements PyElementEx {

	public PyElementImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	public String toString() {
		String className = getClass().getName();
		int pos = className.lastIndexOf('.');
		if (pos >= 0) {
			className = className.substring(pos + 1);
		}
		if (className.endsWith("Impl")) {
			className = className.substring(0, className.length() - 4);
		}
		return className;
	}

	@Override
	public PsiReference getReference() {
		PsiReference[] refs = getReferences();
		PsiReference result;

		if (refs.length == 0) {
			result = null;
		} else {
			result = refs[0];
		}

		return result;
	}

	@Override
	@NotNull
	public PsiReference[] getReferences() {
		return ReferenceProvidersRegistry.getReferencesFromProviders((PyElement) getOriginalElement());
	}

	@Override
	public void accept(PsiElementVisitor visitor) {
		if ((visitor instanceof PyElementVisitor))
			acceptPyVisitor((PyElementVisitor) visitor);
		else
			super.accept(visitor);
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyElement(this);
	}

	protected <T extends PyElementEx> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
		T[] psiElements = (T[]) Array.newInstance(array.getClass().getComponentType(), nodes.length);
		for (int i = 0; i < nodes.length; i++) {
			psiElements[i] = ((T) nodes[i].getPsi());
		}
		return psiElements;
	}

	@NotNull
	protected <T extends PyElementEx> T[] childrenToPsi(TokenSet filterSet, T[] array) {
		ASTNode[] nodes = getNode().getChildren(filterSet);

		return nodesToPsi(nodes, array);
	}

	@Nullable
	protected <T extends PyElementEx> T childToPsi(TokenSet filterSet, int index) {
		ASTNode[] nodes = getNode().getChildren(filterSet);
		if (nodes.length <= index) {
			return null;
		}

		return (T) nodes[index].getPsi();
	}

	@Nullable
	protected <T extends PyElementEx> T childToPsi(IElementType elType) {
		ASTNode node = getNode().findChildByType(elType);
		if (node == null) {
			return null;
		}

		return (T) node.getPsi();
	}

	@NotNull
	protected <T extends PyElementEx> T childToPsiNotNull(TokenSet filterSet, int index) {
		PyElement child = childToPsi(filterSet, index);
		if (child == null)
			throw new RuntimeException("child must not be null");
		return ((T) child);
	}

	@NotNull
	protected <T extends PyElement> T childToPsiNotNull(IElementType elType) {
		return (T) findChildByType(elType);
	}

	@Override
	@Nullable
	public <T extends PyElement> T getContainingElement(Class<T> aClass) {
		PsiElement parent = getParent();
		while (parent != null) {
			if (aClass.isInstance(parent)) {
				return (T) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	@Override
	@Nullable
	public PyElement getContainingElement(TokenSet tokenSet) {
		PsiElement parent = getParent();
		while (parent != null) {
			ASTNode node = parent.getNode();
			if ((node != null) && (tokenSet.contains(node.getElementType()))) {
				return (PyElement) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	@Override
	public void delete() throws IncorrectOperationException {
		PsiElement parent = getParent();
		if ((parent instanceof PyElementImpl)) {
			PyElementImpl pyElement = (PyElementImpl) parent;
			pyElement.deletePyChild(this);
		} else {
			super.delete();
		}
	}

	@Override
	public PsiElement replace(PsiElement element) throws IncorrectOperationException {
		PsiElement parent = getParent();
		if ((parent instanceof PyElementImpl)) {
			PyElementImpl pyElement = (PyElementImpl) parent;
			return pyElement.replacePyChild(this, element);
		}
		return super.replace(element);
	}

	protected void deletePyChild(PyElementImpl element)
			throws IncorrectOperationException {
		throw new IncorrectOperationException("Delete not implemented in " + this);
	}

	protected PsiElement replacePyChild(PyElement oldel, PsiElement newel) throws IncorrectOperationException {
		if (!oldel.getParent().equals(this)) {
			throw new IncorrectOperationException("Element " + oldel + " is " + "not my child");
		}

		Class cls = getValidChildClass();
		if (cls == null) {
			throw new IncorrectOperationException("Delete not imlpemented for " + this);
		}

		if ((!cls.isInstance(oldel)) || (!cls.isInstance(newel))) {
			throw new IncorrectOperationException("Elements must be instance of " + cls.getSimpleName() + ", but are " + oldel + ", " + newel);
		}

		PsiElement copy = newel.copy();
		getNode().replaceChild(oldel.getNode(), copy.getNode());
		return copy;
	}

	@Nullable
	protected Class<? extends PsiElement> getValidChildClass() {
		return null;
	}

	protected ASTNode getPrevComma(ASTNode after) {
		ASTNode node = after;

		do {
			node = node.getTreePrev();
		}
		while ((node != null) && (!node.getElementType().equals(PyTokenTypes.COMMA)));
		return node;
	}

	protected ASTNode getNextComma(ASTNode after) {
		ASTNode node = after;

		do {
			node = node.getTreeNext();
		}
		while ((node != null) && (!node.getElementType().equals(PyTokenTypes.COMMA)));
		return node;
	}
}