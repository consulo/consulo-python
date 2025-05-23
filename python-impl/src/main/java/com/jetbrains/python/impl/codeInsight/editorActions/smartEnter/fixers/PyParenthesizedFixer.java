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
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import consulo.language.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:42:08
 */
public class PyParenthesizedFixer extends PyFixer<PyParenthesizedExpression>
{
	public PyParenthesizedFixer()
	{
		super(PyParenthesizedExpression.class);
	}

	@Override
	public void doApply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull PyParenthesizedExpression expression) throws IncorrectOperationException
	{
		final PsiElement lastChild = expression.getLastChild();
		if(lastChild != null && !")".equals(lastChild.getText()))
		{
			editor.getDocument().insertString(expression.getTextRange().getEndOffset(), ")");
		}
	}
}
