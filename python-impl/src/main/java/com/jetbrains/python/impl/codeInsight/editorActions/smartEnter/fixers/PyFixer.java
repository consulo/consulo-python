/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyElement;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:10:33
 */
public abstract class PyFixer<T extends PyElement>
{
	private final Class<T> myClass;

	public PyFixer(@Nonnull Class<T> aClass)
	{
		myClass = aClass;
	}

	@SuppressWarnings("unchecked")
	public final void apply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull PsiElement element) throws IncorrectOperationException
	{
		if(myClass.isInstance(element) && isApplicable(editor, (T) element))
		{
			doApply(editor, processor, (T) element);
		}
	}

	protected boolean isApplicable(@Nonnull Editor editor, @Nonnull T element)
	{
		return myClass.isInstance(element);
	}

	protected abstract void doApply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull T element);
}
