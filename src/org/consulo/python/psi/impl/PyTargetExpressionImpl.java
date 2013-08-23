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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.consulo.python.PyTokenTypes;
import org.consulo.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyTargetExpressionImpl extends PyElementImpl
		implements PyTargetExpression {
	public PyTargetExpressionImpl(ASTNode astNode) {
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyTargetExpression(this);
	}

	@Override
	@Nullable
	public String getName() {
		ASTNode node = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
		return node != null ? node.getText() : null;
	}

	@Override
	public PsiElement setName(String name) throws IncorrectOperationException {
		ASTNode nameElement = PyElementGenerator.getInstance().createNameIdentifier(getProject(), name);
		getNode().replaceChild(getNode().getFirstChildNode(), nameElement);
		return this;
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
}