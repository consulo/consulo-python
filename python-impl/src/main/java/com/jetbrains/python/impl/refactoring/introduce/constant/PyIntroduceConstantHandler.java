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
package com.jetbrains.python.impl.refactoring.introduce.constant;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class PyIntroduceConstantHandler extends IntroduceHandler
{
	public PyIntroduceConstantHandler()
	{
		super(new ConstantValidator(), PyLocalize.refactoringIntroduceConstantDialogTitle());
	}

	@Override
    @RequiredWriteAction
	protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation)
	{
		if(PsiTreeUtil.getParentOfType(expression, ScopeOwner.class) instanceof PyFile)
		{
			return super.replaceExpression(expression, newExpression, operation);
		}
		return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
	}

	@Override
	@RequiredWriteAction
	protected PsiElement addDeclaration(PsiElement expression, PsiElement declaration, IntroduceOperation operation)
	{
		PsiElement containingFile = expression.getContainingFile();
		assert containingFile instanceof PyFile;
		PsiElement initialPosition = AddImportHelper.getFileInsertPosition((PyFile) containingFile);

		List<PsiElement> sameFileRefs = collectReferencedDefinitionsInSameFile(operation.getElement(), operation.getFile());
		PsiElement maxPosition = getLowermostTopLevelStatement(sameFileRefs);

		if(maxPosition == null)
		{
			return containingFile.addBefore(declaration, initialPosition);
		}

		assert PyUtil.isTopLevel(maxPosition);
		return containingFile.addAfter(declaration, maxPosition);
	}

	@Override
    @RequiredReadAction
	protected Collection<String> generateSuggestedNames(PyExpression expression)
	{
		Collection<String> names = new HashSet<>();
		for(String name : super.generateSuggestedNames(expression))
		{
			names.add(StringUtil.toUpperCase(name));
		}
		return names;
	}

	@Override
	protected boolean isValidIntroduceContext(PsiElement element)
	{
		return super.isValidIntroduceContext(element) || PsiTreeUtil.getParentOfType(element, PyParameterList.class) != null;
	}

	@Override
	@RequiredReadAction
	protected boolean checkEnabled(IntroduceOperation operation)
	{
		PsiElement selectionElement = getOriginalSelectionCoveringElement(operation.getElement());

		PsiFile containingFile = selectionElement.getContainingFile();
		if(!(containingFile instanceof PyFile))
		{
			return false;
		}

		Editor editor = operation.getEditor();
		if(editor == null)
		{
			return false;
		}

		List<PsiElement> sameFileRefs = collectReferencedDefinitionsInSameFile(operation.getElement(), operation.getFile());
		if(!ContainerUtil.all(sameFileRefs, it -> PyUtil.isTopLevel(it)))
		{
			return false;
		}
		PsiElement maxPosition = getLowermostTopLevelStatement(sameFileRefs);
		if(maxPosition == null)
		{
			return true;
		}
		return PsiUtilCore.compareElementsByPosition(maxPosition, selectionElement) <= 0 &&
			!PsiTreeUtil.isAncestor(maxPosition, selectionElement, false);
	}

	private static @Nullable PsiElement getLowermostTopLevelStatement(List<PsiElement> elements)
	{
		return StreamEx.of(elements)
			.map(it -> PyPsiUtils.getParentRightBefore(it, it.getContainingFile()))
			.select(PyStatement.class)
			.max(PsiUtilCore::compareElementsByPosition)
			.orElse(null);
	}

	@Override
	@RequiredUIAccess
	protected void showCanNotIntroduceErrorHint(Project project, Editor editor)
	{
		String message =
			RefactoringBundle.getCannotRefactorMessage(PyLocalize.refactoringIntroduceConstantCannotExtractSelectedExpression().get());
		CommonRefactoringUtil.showErrorHint(project, editor, LocalizeValue.of(message), myDialogTitle, getHelpId());
	}

	@Override
	protected String getHelpId()
	{
		return "python.reference.introduceConstant";
	}

	@Override
	protected String getRefactoringId()
	{
		return "refactoring.python.introduce.constant";
	}
}
