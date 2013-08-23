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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.consulo.python.PythonFileType;
import org.consulo.python.PythonLanguage;
import org.consulo.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PyFileImpl extends PsiFileBase implements PyFile {

	public PyFileImpl(@NotNull FileViewProvider viewProvider) {
		super(viewProvider, PythonLanguage.INSTANCE);
	}

	@Override
	@NotNull
	public FileType getFileType() {
		return PythonFileType.INSTANCE;
	}

	@Override
	public void accept(PsiElementVisitor visitor) {
		if ((visitor instanceof PyElementVisitor))
			((PyElementVisitor) visitor).visitPyFile(this);
		else
			super.accept(visitor);
	}

	@Override
	public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
		PsiElement[] children = getChildren();
		for (PsiElement child : children) {
			if (!child.processDeclarations(processor, state, lastParent, place)) {
				return false;
			}
		}
		return true;
	}

	@Override
	@Nullable
	public <T extends PyElement> T getContainingElement(Class<T> aClass) {
		return null;
	}

	@Override
	@Nullable
	public PyElement getContainingElement(TokenSet tokenSet) {
		return null;
	}

	@Override
	@PsiCached
	public Collection<PyStatement> getStatements() {
		return PsiTreeUtil.findChildrenOfType(this, PyStatement.class);
	}
}