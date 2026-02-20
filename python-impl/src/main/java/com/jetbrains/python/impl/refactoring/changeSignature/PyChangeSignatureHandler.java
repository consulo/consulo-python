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
package com.jetbrains.python.impl.refactoring.changeSignature;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User : ktisha
 */
public class PyChangeSignatureHandler implements ChangeSignatureHandler
{
	@Nullable
	@Override
	public PsiElement findTargetMember(PsiFile file, Editor editor)
	{
		PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
		return findTargetMember(element);
	}

	@Nullable
	@Override
	public PsiElement findTargetMember(@Nullable PsiElement element)
	{
		PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
		if (callExpression != null)
		{
			return callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
		}
		return PsiTreeUtil.getParentOfType(element, PyFunction.class);
	}

	@Override
	@RequiredUIAccess
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		PsiElement element = findTargetMember(file, editor);
		if (element == null)
		{
			element = dataContext.getData(PsiElement.KEY);
		}
		invokeOnElement(project, element, editor);
	}

	@Override
	@RequiredUIAccess
	public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, @Nullable DataContext dataContext)
	{
		if (elements.length != 1)
		{
			return;
		}
		Editor editor = dataContext == null ? null : dataContext.getData(Editor.KEY);
		invokeOnElement(project, elements[0], editor);
	}

	@Nullable
	@Override
	public String getTargetNotFoundMessage()
	{
		return PyLocalize.refactoringChangeSignatureErrorWrongCaretPositionMethodName().get();
	}

	@RequiredUIAccess
	private static void invokeOnElement(Project project, PsiElement element, Editor editor)
	{
		if (element instanceof PyLambdaExpression)
		{
			showCannotRefactorErrorHint(project, editor, PyLocalize.refactoringChangeSignatureErrorLambdaCall().get());
			return;
		}
		if (!(element instanceof PyFunction))
		{
			showCannotRefactorErrorHint(project, editor, PyLocalize.refactoringChangeSignatureErrorWrongCaretPositionMethodName().get());
			return;
		}

		if (isNotUnderSourceRoot(project, element.getContainingFile()))
		{
			showCannotRefactorErrorHint(project, editor, PyLocalize.refactoringChangeSignatureErrorNotUnderSourceRoot().get());
			return;
		}

		PyFunction superMethod = getSuperMethod((PyFunction) element);
		if (superMethod == null)
		{
			return;
		}
		if (!superMethod.equals(element))
		{
			element = superMethod;
			if (isNotUnderSourceRoot(project, superMethod.getContainingFile()))
			{
				return;
			}
		}

		PyFunction function = (PyFunction) element;
		PyParameter[] parameters = function.getParameterList().getParameters();
		for (PyParameter p : parameters)
		{
			if (p instanceof PyTupleParameter)
			{
				showCannotRefactorErrorHint(project, editor, PyLocalize.refactoringChangeSignatureErrorTupleParameters().get());
				return;
			}
		}

		PyMethodDescriptor method = new PyMethodDescriptor((PyFunction) element);
		PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, method);
		dialog.show();
	}

	@RequiredUIAccess
	private static void showCannotRefactorErrorHint(@Nonnull Project project, @Nullable Editor editor, @Nonnull String details)
	{
		String message = RefactoringBundle.getCannotRefactorMessage(details);
		CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME.get(), REFACTORING_NAME.get());
	}

	private static boolean isNotUnderSourceRoot(@Nonnull Project project, @Nullable PsiFile psiFile)
	{
		if (psiFile == null)
		{
			return true;
		}
		VirtualFile virtualFile = psiFile.getVirtualFile();
		if (virtualFile != null)
		{
			ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
			if (fileIndex.isExcluded(virtualFile) || (fileIndex.isInLibraryClasses(virtualFile) && !fileIndex.isInContent(virtualFile)))
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	@RequiredUIAccess
	protected static PyFunction getSuperMethod(@Nullable PyFunction function)
	{
		if (function == null)
		{
			return null;
		}
		PyClass containingClass = function.getContainingClass();
		if (containingClass == null)
		{
			return function;
		}
		PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
		if (!deepestSuperMethod.equals(function))
		{
			PyClass baseClass = deepestSuperMethod.getContainingClass();
			PyBuiltinCache cache = PyBuiltinCache.getInstance(baseClass);
			String baseClassName = baseClass == null ? "" : baseClass.getName();
			if (cache.isBuiltin(baseClass))
			{
				return function;
			}
			LocalizeValue message =
				PyLocalize.refactoringChangeSignatureFindUsagesOfBaseClass(function.getName(), containingClass.getName(), baseClassName);
			int choice;
			if (function.getApplication().isUnitTestMode())
			{
				choice = Messages.YES;
			}
			else
			{
				choice = Messages.showYesNoCancelDialog(function.getProject(), message.get(), REFACTORING_NAME.get(), UIUtil.getQuestionIcon());
			}
			switch (choice)
			{
				case Messages.YES:
					return deepestSuperMethod;
				case Messages.NO:
					return function;
				default:
					return null;
			}
		}
		return function;
	}
}
