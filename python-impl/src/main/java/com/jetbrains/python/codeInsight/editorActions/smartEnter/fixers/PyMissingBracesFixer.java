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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PySliceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:55:46
 */
public class PyMissingBracesFixer extends PyFixer<PyElement>
{
	public PyMissingBracesFixer()
	{
		super(PyElement.class);
	}

	@Override
	public void doApply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull PyElement psiElement) throws IncorrectOperationException
	{
		if(psiElement instanceof PySetLiteralExpression || psiElement instanceof PyDictLiteralExpression)
		{
			final PsiElement lastChild = PyPsiUtils.getPrevNonCommentSibling(psiElement.getLastChild(), false);
			if(lastChild != null && !"}".equals(lastChild.getText()))
			{
				editor.getDocument().insertString(lastChild.getTextRange().getEndOffset(), "}");
			}
		}
		else if(psiElement instanceof PyListLiteralExpression ||
				psiElement instanceof PySliceExpression ||
				psiElement instanceof PySubscriptionExpression)
		{
			final PsiElement lastChild = PyPsiUtils.getPrevNonCommentSibling(psiElement.getLastChild(), false);
			if(lastChild != null && !"]".equals(lastChild.getText()))
			{
				editor.getDocument().insertString(lastChild.getTextRange().getEndOffset(), "]");
			}
		}
	}
}
