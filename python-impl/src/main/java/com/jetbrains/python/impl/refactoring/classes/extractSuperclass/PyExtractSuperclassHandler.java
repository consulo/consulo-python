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
package com.jetbrains.python.impl.refactoring.classes.extractSuperclass;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.project.Project;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.impl.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.impl.vp.Creator;
import com.jetbrains.python.impl.vp.ViewPresenterUtils;

/**
 * @author Dennis.Ushakov
 */
public class PyExtractSuperclassHandler extends PyClassRefactoringHandler
{
	public static final String REFACTORING_NAME = RefactoringBundle.message("extract.superclass.title");


	@Override
	protected void doRefactorImpl(@Nonnull final Project project, @Nonnull final PyClass classUnderRefactoring, @Nonnull final PyMemberInfoStorage infoStorage, @Nonnull final Editor editor)
	{
		//TODO: Move to presenter
		if(PyUtil.filterOutObject(infoStorage.getClassMemberInfos(classUnderRefactoring)).isEmpty())
		{
			CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.extract.super.class.no.members.allowed"), RefactoringBundle.message("extract.superclass.elements" +
					".header"), null);
			return;
		}

		ViewPresenterUtils.linkViewWithPresenterAndLaunch(PyExtractSuperclassPresenter.class, PyExtractSuperclassView.class, new Creator<PyExtractSuperclassView, PyExtractSuperclassPresenter>()
		{
			@Nonnull
			@Override
			public PyExtractSuperclassPresenter createPresenter(@Nonnull final PyExtractSuperclassView view)
			{
				return new PyExtractSuperclassPresenterImpl(view, classUnderRefactoring, infoStorage);
			}

			@Nonnull
			@Override
			public PyExtractSuperclassView createView(@Nonnull final PyExtractSuperclassPresenter presenter)
			{
				return new PyExtractSuperclassViewSwingImpl(classUnderRefactoring, project, presenter);
			}
		});
	}


	@Override
	protected String getTitle()
	{
		return REFACTORING_NAME;
	}

	@Override
	protected String getHelpId()
	{
		return "refactoring.extractSuperclass";
	}
}
