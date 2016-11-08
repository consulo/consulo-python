/*
 * Copyright 2013-2016 must-be.org
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

package com.jetbrains.python.console;

import java.util.Arrays;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;

/**
 * @author VISTALL
 * @since 09-Nov-16
 */
public class PyConsoleEnterHandler
{
	public boolean handleEnterPressed(EditorEx editor)
	{
		Project project = editor.getProject();
		if(project == null)
		{
			throw new IllegalArgumentException();
		}

		int lineCount = editor.getDocument().getLineCount();
		if(lineCount > 0)
		{ // move to end of line
			editor.getSelectionModel().removeSelection();
			LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
			if(caretPosition.line == lineCount - 1)
			{
				// we can move caret if only it's on the last line of command
				int lineEndOffset = editor.getDocument().getLineEndOffset(caretPosition.line);
				editor.getCaretModel().moveToOffset(lineEndOffset);
			}
			else
			{
				// otherwise just process enter action
				executeEnterHandler(project, editor);
				return false;
			}
		}
		else
		{
			return true;
		}
		PsiDocumentManager psiMgr = PsiDocumentManager.getInstance(project);
		psiMgr.commitDocument(editor.getDocument());

		int caretOffset = editor.getExpectedCaretOffset();
		PsiElement atElement = findFirstNoneSpaceElement(psiMgr.getPsiFile(editor.getDocument()), caretOffset);
		if(atElement == null)
		{
			executeEnterHandler(project, editor);
			return false;
		}

		String firstLine = getLineAtOffset(editor.getDocument(), DocumentUtil.getFirstNonSpaceCharOffset(editor.getDocument(), 0));
		boolean isCellMagic = firstLine.trim().startsWith("%%") && !StringUtil.trimEnd(firstLine, ' ').endsWith("?");
		String prevLine = getLineAtOffset(editor.getDocument(), caretOffset);

		boolean isLineContinuation = StringUtil.endsWithChar(prevLine.trim(), '\\');
		boolean insideDocString = isElementInsideDocString(atElement, caretOffset);
		boolean isMultiLineCommand = PsiTreeUtil.getParentOfType(atElement, PyStatementListContainer.class) != null || isCellMagic;
		boolean isAtTheEndOfCommand = editor.getDocument().getLineNumber(caretOffset) == editor.getDocument().getLineCount() - 1;

		boolean hasCompleteStatement = !insideDocString && !isLineContinuation && checkComplete(atElement);

		executeEnterHandler(project, editor);

		return isAtTheEndOfCommand && hasCompleteStatement && ((isMultiLineCommand && prevLine.isEmpty()) || (!isMultiLineCommand));
	}

	private boolean checkComplete(PsiElement el)
	{
		PyStatementListContainer compoundStatement = PsiTreeUtil.getParentOfType(el, PyStatementListContainer.class);
		if(compoundStatement != null && !(compoundStatement instanceof PyTryPart))
		{
			return compoundStatement.getStatementList().getStatements().length != 0;
		}
		PsiElement topLevel = PyPsiUtils.getParentRightBefore(el, el.getContainingFile());
		return topLevel != null && !PsiTreeUtil.hasErrorElements(topLevel);
	}

	private boolean isElementInsideDocString(PsiElement atElement, int caretOffset)
	{
		return atElement.getContext() instanceof PyStringLiteralExpression &&
				(PyTokenTypes.TRIPLE_NODES.contains(atElement.getNode().getElementType()) || atElement.getNode().getElementType() == PyTokenTypes.DOCSTRING) &&
				isMultilineString(atElement.getText()) &&
				(atElement.getTextRange().getEndOffset() > caretOffset || !isCompleteDocString(atElement.getText()));
	}

	private boolean isMultilineString(String str)
	{
		String text = str.substring(PyStringLiteralExpressionImpl.getPrefixLength(str));
		return text.startsWith("\"\"\"") || text.startsWith("'''");
	}

	private boolean isCompleteDocString(String str)
	{
		int prefixLen = PyStringLiteralExpressionImpl.getPrefixLength(str);
		String text = str.substring(prefixLen);
		for(String token : Arrays.asList("\"\"\"", "'''"))
		{
			if(text.length() >= 2 * token.length() && text.startsWith(token) && text.endsWith(token))
			{
				return true;
			}
		}

		return false;
	}

	private PsiElement findFirstNoneSpaceElement(PsiFile psiFile, int offset)
	{
		for(int i = offset; i <= 0; i--)
		{
			PsiElement el = psiFile.findElementAt(i);
			if(el != null && !(el instanceof PsiWhiteSpace))
			{
				return el;
			}
		}
		return null;
	}

	private String getLineAtOffset(Document doc, int offset)
	{
		int line = doc.getLineNumber(offset);
		int start = doc.getLineStartOffset(line);
		int end = doc.getLineEndOffset(line);
		return doc.getText(new TextRange(start, end));
	}

	private void executeEnterHandler(Project project, EditorEx editor)
	{
		EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
		new WriteCommandAction<Void>(project)
		{
			@Override
			protected void run(Result<Void> result) throws Throwable
			{
				enterHandler.execute(editor, null, DataManager.getInstance().getDataContext(editor.getComponent()));

			}
		}.execute();
	}

}
