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

package ru.yole.pythonid;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.psi.*;

public class PythonFindUsagesProvider
		implements FindUsagesProvider {
	private PythonLanguage language;

	public PythonFindUsagesProvider(PythonLanguage language) {
		this.language = language;
	}

	@Override
	public boolean canFindUsagesFor(PsiElement psiElement) {
		return ((psiElement instanceof PsiNamedElement)) || ((psiElement instanceof PyReferenceExpression));
	}

	@Override
	public String getHelpId(PsiElement psiElement) {
		return null;
	}

	@Override
	@NotNull
	public String getType(PsiElement element) {
		if ((element instanceof PyParameter)) {
			String tmp9_7 = "parameter";
			if (tmp9_7 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp9_7;
		}
		if ((element instanceof PyFunction)) {
			String tmp33_31 = "function";
			if (tmp33_31 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp33_31;
		}
		if ((element instanceof PyClass)) {
			String tmp57_55 = "class";
			if (tmp57_55 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp57_55;
		}
		if (((element instanceof PyReferenceExpression)) || ((element instanceof PyTargetExpression))) {
			String tmp88_86 = "variable";
			if (tmp88_86 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp88_86;
		}
		String tmp105_103 = "";
		if (tmp105_103 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp105_103;
	}

	@Override
	@NotNull
	public String getDescriptiveName(PsiElement element) {
		if ((element instanceof PsiNamedElement)) {
			String tmp16_11 = ((PsiNamedElement) element).getName();
			if (tmp16_11 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp16_11;
		}
		if ((element instanceof PyReferenceExpression)) {
			String referencedName = ((PyReferenceExpression) element).getReferencedName();
			if (referencedName == null) {
				String tmp54_52 = "";
				if (tmp54_52 == null) throw new IllegalStateException("@NotNull method must not return null");
				return tmp54_52;
			}
			String tmp70_69 = referencedName;
			if (tmp70_69 == null) throw new IllegalStateException("@NotNull method must not return null");
			return tmp70_69;
		}
		String tmp87_85 = "";
		if (tmp87_85 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp87_85;
	}

	@Override
	@NotNull
	public String getNodeText(PsiElement element, boolean useFullName) {
		String tmp5_2 = getDescriptiveName(element);
		if (tmp5_2 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp5_2;
	}

	public boolean mayHaveReferences(IElementType token, short searchContext) {
		if (((searchContext & 0x1) != 0) && (token == this.language.getElementTypes().REFERENCE_EXPRESSION))
			return true;
		if (((searchContext & 0x2) != 0) && (token == this.language.getTokenTypes().END_OF_LINE_COMMENT))
			return true;
		if (((searchContext & 0x4) != 0) && (token == this.language.getTokenTypes().STRING_LITERAL))
			return true;
		return false;
	}

	@Override
	public WordsScanner getWordsScanner() {
		return new PyWordsScanner(this.language);
	}
}