/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections.quickfix;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;

/**
 * @author traff
 */
public class PyQuickFixUtil
{
	@Nullable
	public static Editor getEditor(@Nonnull PsiElement element)
	{
		Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
		if(document != null)
		{
			final EditorFactory instance = EditorFactory.getInstance();
			if(instance == null)
			{
				return null;
			}
			Editor[] editors = instance.getEditors(document);
			if(editors.length > 0)
			{
				return editors[0];
			}
		}
		return null;
	}
}
