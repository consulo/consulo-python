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

import static com.jetbrains.python.impl.psi.PyUtil.sure;

import jakarta.annotation.Nonnull;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.impl.PyPsiUtils;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   16:03:43
 */
public class PyForPartFixer extends PyFixer<PyForPart>
{
	public PyForPartFixer()
	{
		super(PyForPart.class);
	}

	@Override
	public void doApply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull PyForPart forPart)
	{
		final PsiElement colon = PyPsiUtils.getFirstChildOfType(forPart, PyTokenTypes.COLON);
		final Document document = editor.getDocument();
		final PsiElement forToken = PyPsiUtils.getFirstChildOfType(forPart, PyTokenTypes.FOR_KEYWORD);
		if(colon == null)
		{
			String textToInsert = ":";
			PsiElement sourceOrTarget = forPart.getSource();
			PsiElement positionToInsert = sourceOrTarget;
			if(sourceOrTarget == null)
			{
				sourceOrTarget = forPart.getTarget();
				final PsiElement inToken = PyPsiUtils.getFirstChildOfType(forPart, PyTokenTypes.IN_KEYWORD);
				if(inToken == null)
				{
					if(sourceOrTarget == null)
					{
						positionToInsert = sure(forToken);
						textToInsert = "  in :";
						processor.registerUnresolvedError(positionToInsert.getTextRange().getEndOffset() + 1);
					}
					else
					{
						positionToInsert = sourceOrTarget;
						textToInsert = " in :";
						processor.registerUnresolvedError(positionToInsert.getTextRange().getEndOffset() + 4);
					}
				}
				else
				{
					positionToInsert = inToken;
					textToInsert = " :";
					processor.registerUnresolvedError(positionToInsert.getTextRange().getEndOffset() + 1);
				}
			}
			document.insertString(positionToInsert.getTextRange().getEndOffset(), textToInsert);
		}
	}
}
