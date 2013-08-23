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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PythonLanguage;
import ru.yole.pythonid.psi.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class PyFileImpl extends PsiFileBase
		implements PyFile {
	private final FileType fileType;

	public PyFileImpl(Project project, VirtualFile file, PythonLanguage language, FileType pythonFileType) {
		super(project, file, language);
		this.fileType = pythonFileType;
	}

	public PyFileImpl(Project project, String name, CharSequence text, PythonLanguage language, FileType pythonFileType) {
		super(project, name, text, language);
		this.fileType = pythonFileType;
	}

	@NotNull
	public FileType getFileType() {
		FileType tmp4_1 = this.fileType;
		if (tmp4_1 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp4_1;
	}

	public String toString() {
		return "PyFile:" + getName();
	}

	public Icon getIcon(int i) {
		return this.fileType.getIcon();
	}

	public void accept(PsiElementVisitor visitor) {
		if ((visitor instanceof PyElementVisitor))
			((PyElementVisitor) visitor).visitPyFile(this);
		else
			super.accept(visitor);
	}

	public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
		PsiElement[] children = getChildren();
		for (PsiElement child : children) {
			if (!child.processDeclarations(processor, substitutor, lastParent, place)) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	public <T extends PyElement> T getContainingElement(Class<T> aClass) {
		return null;
	}

	@Nullable
	public PyElement getContainingElement(TokenSet tokenSet) {
		return null;
	}

	@PsiCached
	public List<PyStatement> getStatements() {
		List stmts = new ArrayList();
		for (PsiElement child : getChildren()) {
			if ((child instanceof PyStatement)) {
				PyStatement statement = (PyStatement) child;
				stmts.add(statement);
			}
		}
		return stmts;
	}

	public AbstractPythonLanguage getPyLanguage() {
		return (AbstractPythonLanguage) getLanguage();
	}
}