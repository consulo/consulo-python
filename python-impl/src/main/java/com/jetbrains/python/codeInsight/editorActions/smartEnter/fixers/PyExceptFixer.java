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

import static com.jetbrains.python.psi.PyUtil.sure;

import javax.annotation.Nonnull;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   22.04.2010
 * Time:   18:13:34
 */
public class PyExceptFixer extends PyFixer<PyExceptPart>
{
	public PyExceptFixer()
	{
		super(PyExceptPart.class);
	}

	@Override
	public void doApply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull PyExceptPart exceptPart) throws IncorrectOperationException
	{
		final PsiElement colon = PyPsiUtils.getFirstChildOfType(exceptPart, PyTokenTypes.COLON);
		if(colon == null)
		{
			final PsiElement exceptToken = PyPsiUtils.getFirstChildOfType(exceptPart, PyTokenTypes.EXCEPT_KEYWORD);
			int offset = sure(exceptToken).getTextRange().getEndOffset();
			final PyExpression exceptClass = exceptPart.getExceptClass();
			if(exceptClass != null)
			{
				offset = exceptClass.getTextRange().getEndOffset();
			}
			final PyExpression target = exceptPart.getTarget();
			if(target != null)
			{
				offset = target.getTextRange().getEndOffset();
			}
			editor.getDocument().insertString(offset, ":");
		}
	}
}