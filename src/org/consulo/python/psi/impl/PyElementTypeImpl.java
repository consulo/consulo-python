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
import org.consulo.python.PythonLanguage;
import org.consulo.python.psi.PyElementType;
import org.jetbrains.annotations.Nullable;

public class PyElementTypeImpl extends PyElementType {
	private Class<? extends PsiElement> _psiElementClass;
	private static final Class[] PARAMETER_TYPES = {ASTNode.class};

	public PyElementTypeImpl(String debugName) {
		super(debugName, PythonLanguage.INSTANCE);
	}

	public PyElementTypeImpl(String debugName, Class<? extends PsiElement> psiElementClass) {
		this(debugName);
		_psiElementClass = psiElementClass;
	}

	@Override
	public Class<? extends PsiElement> getElementClass() {
		return _psiElementClass;
	}

	@Override
	@Nullable
	public PsiElement createElement(ASTNode node) {
		if (_psiElementClass == null) {
			return null;
		}

		try {
			return _psiElementClass.getConstructor(PARAMETER_TYPES).newInstance(node);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String toString() {
		return "Py:" + super.toString();
	}
}