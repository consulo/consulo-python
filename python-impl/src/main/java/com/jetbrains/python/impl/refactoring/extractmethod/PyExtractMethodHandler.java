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
package com.jetbrains.python.impl.refactoring.extractmethod;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.language.editor.codeFragment.CannotCreateCodeFragmentException;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.Couple;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author oleg
 */
public class PyExtractMethodHandler implements RefactoringActionHandler
{
	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		// select editor text fragment
		if(!editor.getSelectionModel().hasSelection())
		{
			editor.getSelectionModel().selectLineAtCaret();
		}
		invokeOnEditor(project, editor, file);
	}

	@Override
	public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext)
	{
	}

	private static void invokeOnEditor(Project project, Editor editor, PsiFile file)
	{
		CommonRefactoringUtil.checkReadOnlyStatus(project, file);
		PsiElement element1 = null;
		PsiElement element2 = null;
		SelectionModel selectionModel = editor.getSelectionModel();
		if(selectionModel.hasSelection())
		{
			element1 = file.findElementAt(selectionModel.getSelectionStart());
			element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
		}
		else
		{
			CaretModel caretModel = editor.getCaretModel();
			Document document = editor.getDocument();
			int lineNumber = document.getLineNumber(caretModel.getOffset());
			if((lineNumber >= 0) && (lineNumber < document.getLineCount()))
			{
				element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
				element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
			}
		}
		// Pass comments and whitespaces
		element1 = PyPsiUtils.getNextSignificantLeaf(element1, false);
		element2 = PyPsiUtils.getPrevSignificantLeaf(element2, false);
		if(element1 == null || element2 == null)
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.bad.selection"), RefactoringBundle.message("extract.method.title"), "refactoring" +
					".extractMethod");
			return;
		}
		if(rangeBelongsToSameClassBody(element1, element2))
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.class.level"), RefactoringBundle.message("extract.method.title"), "refactoring" +
					".extractMethod");
			return;
		}

		Couple<PsiElement> statements = getStatementsRange(element1, element2);
		if(statements != null)
		{
			ScopeOwner owner = PsiTreeUtil.getParentOfType(statements.getFirst(), ScopeOwner.class);
			if(owner == null)
			{
				return;
			}
			PyCodeFragment fragment;
			try
			{
				fragment = PyCodeFragmentUtil.createCodeFragment(owner, element1, element2);
			}
			catch(CannotCreateCodeFragmentException e)
			{
				CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
				return;
			}
			PyExtractMethodUtil.extractFromStatements(project, editor, fragment, statements.getFirst(), statements.getSecond());
			return;
		}

		PsiElement expression = PyRefactoringUtil.getSelectedExpression(project, file, element1, element2);
		if(expression != null)
		{
			ScopeOwner owner = PsiTreeUtil.getParentOfType(element1, ScopeOwner.class);
			if(owner == null)
			{
				return;
			}
			PyCodeFragment fragment;
			try
			{
				fragment = PyCodeFragmentUtil.createCodeFragment(owner, element1, element2);
			}
			catch(CannotCreateCodeFragmentException e)
			{
				CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
				return;
			}
			PyExtractMethodUtil.extractFromExpression(project, editor, fragment, expression);
			return;
		}

		CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.method.error.bad.selection"), RefactoringBundle.message("extract.method.title"), "refactoring" +
				".extractMethod");
	}

	private static boolean rangeBelongsToSameClassBody(@Nonnull PsiElement element1, @Nonnull PsiElement element2)
	{
		PyClass firstScopeOwner = PsiTreeUtil.getParentOfType(element1, PyClass.class, false, ScopeOwner.class);
		PyClass secondScopeOwner = PsiTreeUtil.getParentOfType(element2, PyClass.class, false, ScopeOwner.class);
		return firstScopeOwner != null && firstScopeOwner == secondScopeOwner;
	}

	@Nullable
	private static Couple<PsiElement> getStatementsRange(PsiElement element1, PsiElement element2)
	{
		PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
		if(parent == null)
		{
			return null;
		}

		PyElement statementList = PyPsiUtils.getStatementList(parent);
		if(statementList == null)
		{
			return null;
		}

		PsiElement statement1 = PyPsiUtils.getParentRightBefore(element1, statementList);
		PsiElement statement2 = PyPsiUtils.getParentRightBefore(element2, statementList);
		if(statement1 == null || statement2 == null)
		{
			return null;
		}

		// return elements if they are really first and last elements of statements
		if(element1 == PsiTreeUtil.getDeepestFirst(statement1) && element2 == PyPsiUtils.getPrevSignificantLeaf(PsiTreeUtil.getDeepestLast(statement2), !(element2 instanceof PsiComment)))
		{
			return Couple.of(statement1, statement2);
		}
		return null;
	}
}
