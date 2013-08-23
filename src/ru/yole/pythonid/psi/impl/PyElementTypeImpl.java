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
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.PythonLanguage;
import ru.yole.pythonid.psi.PyElementType;

public class PyElementTypeImpl extends PyElementType {
	private Class<? extends PsiElement> _psiElementClass;
	private static final Class[] PARAMETER_TYPES = {ASTNode.class, AbstractPythonLanguage.class};

	public PyElementTypeImpl(String debugName, PythonLanguage language) {
		super(debugName, language);
	}

	public PyElementTypeImpl(String debugName, Class<? extends PsiElement> psiElementClass, PythonLanguage language) {
		this(debugName, language);
		this._psiElementClass = psiElementClass;
	}

	public Class<? extends PsiElement> getElementClass() {
		return this._psiElementClass;
	}

	@Nullable
	public PsiElement createElement(ASTNode node, Language language) {
		if (this._psiElementClass == null) {
			return null;
		}

		try {
			return (PsiElement) this._psiElementClass.getConstructor(PARAMETER_TYPES).newInstance(new Object[]{node, language});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public String toString() {
		return "Py:" + super.toString();
	}
}