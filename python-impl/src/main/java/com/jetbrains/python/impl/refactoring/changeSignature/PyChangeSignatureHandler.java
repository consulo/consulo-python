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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.editor.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;

/**
 * User : ktisha
 */

public class PyChangeSignatureHandler implements ChangeSignatureHandler
{
	@Nullable
	@Override
	public PsiElement findTargetMember(PsiFile file, Editor editor)
	{
		final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
		return findTargetMember(element);
	}

	@Nullable
	@Override
	public PsiElement findTargetMember(@Nullable PsiElement element)
	{
		final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
		if(callExpression != null)
		{
			return callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
		}
		return PsiTreeUtil.getParentOfType(element, PyFunction.class);
	}

	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		PsiElement element = findTargetMember(file, editor);
		if(element == null)
		{
			element = dataContext.getData(CommonDataKeys.PSI_ELEMENT);
		}
		invokeOnElement(project, element, editor);
	}

	@Override
	public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, @Nullable DataContext dataContext)
	{
		if(elements.length != 1)
		{
			return;
		}
		final Editor editor = dataContext == null ? null : dataContext.getData(CommonDataKeys.EDITOR);
		invokeOnElement(project, elements[0], editor);
	}

	@Nullable
	@Override
	public String getTargetNotFoundMessage()
	{
		return PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name");
	}

	private static void invokeOnElement(Project project, PsiElement element, Editor editor)
	{
		if(element instanceof PyLambdaExpression)
		{
			showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.lambda.call"));
			return;
		}
		if(!(element instanceof PyFunction))
		{
			showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name"));
			return;
		}

		if(isNotUnderSourceRoot(project, element.getContainingFile()))
		{
			showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.not.under.source.root"));
			return;
		}

		final PyFunction superMethod = getSuperMethod((PyFunction) element);
		if(superMethod == null)
		{
			return;
		}
		if(!superMethod.equals(element))
		{
			element = superMethod;
			if(isNotUnderSourceRoot(project, superMethod.getContainingFile()))
			{
				return;
			}
		}

		final PyFunction function = (PyFunction) element;
		final PyParameter[] parameters = function.getParameterList().getParameters();
		for(PyParameter p : parameters)
		{
			if(p instanceof PyTupleParameter)
			{
				showCannotRefactorErrorHint(project, editor, PyBundle.message("refactoring.change.signature.error.tuple.parameters"));
				return;
			}
		}

		final PyMethodDescriptor method = new PyMethodDescriptor((PyFunction) element);
		final PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, method);
		dialog.show();
	}

	private static void showCannotRefactorErrorHint(@Nonnull Project project, @Nullable Editor editor, @Nonnull String details)
	{
		final String message = RefactoringBundle.getCannotRefactorMessage(details);
		CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME.get(), REFACTORING_NAME.get());
	}

	private static boolean isNotUnderSourceRoot(@Nonnull final Project project, @Nullable final PsiFile psiFile)
	{
		if(psiFile == null)
		{
			return true;
		}
		final VirtualFile virtualFile = psiFile.getVirtualFile();
		if(virtualFile != null)
		{
			final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
			if(fileIndex.isExcluded(virtualFile) || (fileIndex.isInLibraryClasses(virtualFile) && !fileIndex.isInContent(virtualFile)))
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	protected static PyFunction getSuperMethod(@Nullable PyFunction function)
	{
		if(function == null)
		{
			return null;
		}
		final PyClass containingClass = function.getContainingClass();
		if(containingClass == null)
		{
			return function;
		}
		final PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
		if(!deepestSuperMethod.equals(function))
		{
			final PyClass baseClass = deepestSuperMethod.getContainingClass();
			final PyBuiltinCache cache = PyBuiltinCache.getInstance(baseClass);
			final String baseClassName = baseClass == null ? "" : baseClass.getName();
			if(cache.isBuiltin(baseClass))
			{
				return function;
			}
			final String message = PyBundle.message("refactoring.change.signature.find.usages.of.base.class", function.getName(), containingClass.getName(), baseClassName);
			final int choice;
			if(ApplicationManager.getApplication().isUnitTestMode())
			{
				choice = Messages.YES;
			}
			else
			{
				choice = Messages.showYesNoCancelDialog(function.getProject(), message, REFACTORING_NAME.get(), Messages.getQuestionIcon());
			}
			switch(choice)
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

