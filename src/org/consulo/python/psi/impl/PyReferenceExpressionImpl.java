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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyElementTypes;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PyReferenceExpressionImpl extends PyElementImpl
		implements PyReferenceExpression {
	public PyReferenceExpressionImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	public PsiElement getElement() {
		return this;
	}

	@Override
	@NotNull
	public PsiReference[] getReferences() {
		List<PsiReference> refs = new ArrayList<PsiReference>(Arrays.asList(super.getReferences()));
		refs.add(this);
		return refs.toArray(new PsiReference[refs.size()]);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyReferenceExpression(this);
	}

	@Override
	@PsiCached
	@Nullable
	public PyExpression getQualifier() {
		ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
		return (PyExpression) (nodes.length == 1 ? nodes[0].getPsi() : null);
	}

	@Override
	public TextRange getRangeInElement() {
		ASTNode nameElement = getNameElement();
		int startOffset = nameElement != null ? nameElement.getStartOffset() : getNode().getTextRange().getEndOffset();
		return new TextRange(startOffset - getNode().getStartOffset(), getTextLength());
	}

	@Override
	@PsiCached
	@Nullable
	public String getReferencedName() {
		ASTNode nameElement = getNameElement();
		return nameElement != null ? nameElement.getText() : null;
	}

	@PsiCached
	@Nullable
	private ASTNode getNameElement() {
		return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
	}

	@Override
	@Nullable
	public PsiElement resolve() {
		String referencedName = getReferencedName();
		if (referencedName == null) return null;

		if (getQualifier() != null) {
			return null;
		}

		return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), this, this, this);
	}

	@NotNull
	public ResolveResult[] multiResolve(boolean incompleteCode) {
		String referencedName = getReferencedName();
		if (referencedName == null) {
			ResolveResult[] tmp12_9 = ResolveResult.EMPTY_ARRAY;
			if (tmp12_9 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp12_9;
		}
		if (getQualifier() != null) {
			ResolveResult[] tmp37_34 = ResolveResult.EMPTY_ARRAY;
			if (tmp37_34 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp37_34;
		}

		PyResolveUtil.MultiResolveProcessor processor = new PyResolveUtil.MultiResolveProcessor(referencedName);
		PyResolveUtil.treeWalkUp(processor, this, this, this);
		ResolveResult[] tmp73_70 = processor.getResults();
		if (tmp73_70 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp73_70;
	}

	@Override
	public String getCanonicalText() {
		return null;
	}

	@Override
	public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
		ASTNode nameElement = getNameElement();
		if (nameElement != null) {
			ASTNode newNameElement = PyElementGenerator.getInstance().createNameIdentifier(getProject(), newElementName);
			getNode().replaceChild(nameElement, newNameElement);
		}
		return this;
	}

	@Override
	public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
		return null;
	}

	@Override
	public boolean isReferenceTo(PsiElement element) {
		if (((element instanceof PsiNamedElement)) &&
				(Comparing.equal(getReferencedName(), ((PsiNamedElement) element).getName()))) {
			return resolve() == element;
		}

		return false;
	}

	@Override
	public Object[] getVariants() {
		if (getQualifier() != null) {
			return new Object[0];
		}

		PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
		PyResolveUtil.treeWalkUp(processor, this, this, this);
		return processor.getResult();
	}

	@Override
	public boolean isSoft() {
		return false;
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		PsiElement parent = getParent();
		if (((parent instanceof PyStatement)) && (lastParent == null) && (PsiTreeUtil.isAncestor(parent, place, true))) {
			return true;
		}

		if ((getParent() instanceof PyAssignmentStatement)) {
			PsiElement placeParent = place.getParent();
			while ((placeParent != null) && ((placeParent instanceof PyExpression))) {
				placeParent = placeParent.getParent();
			}
			if (placeParent == getParent()) {
				return true;
			}
		}

		if (this == place) {
			return true;
		}
		return processor.execute(this, state);
	}

	@Override
	public boolean shouldHighlightIfUnresolved() {
		return false;
	}

	@Override
	@Nullable
	public String getUnresolvedDescription() {
		return null;
	}

	@Override
	public String toString() {
		return "PyReferenceExpression: " + getReferencedName();
	}
}