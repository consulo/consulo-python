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

package org.consulo.python;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.consulo.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PythonFindUsagesProvider implements FindUsagesProvider {
	@Override
	public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
		return ((psiElement instanceof PsiNamedElement)) || ((psiElement instanceof PyReferenceExpression));
	}

	@Override
	public String getHelpId(@NotNull PsiElement psiElement) {
		return null;
	}

	@Override
	@NotNull
	public String getType(@NotNull PsiElement element) {
		if ((element instanceof PyParameter)) {
			return "parameter";
		}
		if ((element instanceof PyFunction)) {
			return "function";
		}
		if ((element instanceof PyClass)) {
			return "class";
		}
		if (((element instanceof PyReferenceExpression)) || ((element instanceof PyTargetExpression))) {
			return "variable";
		}
		return "";
	}

	@Override
	@NotNull
	public String getDescriptiveName(@NotNull PsiElement element) {
		if ((element instanceof PsiNamedElement)) {
			return ((PsiNamedElement) element).getName();
		}
		if ((element instanceof PyReferenceExpression)) {
			String referencedName = ((PyReferenceExpression) element).getReferencedName();
			if (referencedName == null) {
				return "";
			}
			return referencedName;
		}
		return "";
	}

	@Override
	@NotNull
	public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
		return getDescriptiveName(element);
	}
	@Override
	public WordsScanner getWordsScanner() {
		return new PyWordsScanner();
	}
}