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
import consulo.language.ast.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   19:33:14
 */
public class PyConditionalStatementPartFixer extends PyFixer<PyConditionalStatementPart>
{
	public PyConditionalStatementPartFixer()
	{
		super(PyConditionalStatementPart.class);
	}

	@Override
	public void doApply(@Nonnull Editor editor, @Nonnull PySmartEnterProcessor processor, @Nonnull PyConditionalStatementPart statementPart) throws IncorrectOperationException
	{
		final PyExpression condition = statementPart.getCondition();
		final Document document = editor.getDocument();
		final PsiElement colon = PyPsiUtils.getFirstChildOfType(statementPart, PyTokenTypes.COLON);
		if(colon == null)
		{
			if(condition != null)
			{
				final PsiElement firstNonComment = PyPsiUtils.getNextNonCommentSibling(condition.getNextSibling(), false);
				if(firstNonComment != null && !":".equals(firstNonComment.getNode().getText()))
				{
					document.insertString(firstNonComment.getTextRange().getEndOffset(), ":");
				}
			}
			else
			{
				final TokenSet keywords = TokenSet.create(PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD, PyTokenTypes.WHILE_KEYWORD);
				final PsiElement keywordToken = PyPsiUtils.getChildByFilter(statementPart, keywords, 0);
				final int offset = sure(keywordToken).getTextRange().getEndOffset();
				document.insertString(offset, " :");
				processor.registerUnresolvedError(offset + 1);
			}
		}
		else if(condition == null)
		{
			processor.registerUnresolvedError(colon.getTextRange().getStartOffset());
		}
	}
}
