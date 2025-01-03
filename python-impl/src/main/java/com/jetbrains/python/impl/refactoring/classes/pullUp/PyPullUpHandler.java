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
package com.jetbrains.python.impl.refactoring.classes.pullUp;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.impl.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.impl.vp.Creator;
import com.jetbrains.python.impl.vp.ViewPresenterUtils;

/**
 * @author: Dennis.Ushakov
 */
public class PyPullUpHandler extends PyClassRefactoringHandler
{
	public static final String REFACTORING_NAME = PyBundle.message("refactoring.pull.up.dialog.title");

	@Override
	protected void doRefactorImpl(@Nonnull final Project project, @Nonnull final PyClass classUnderRefactoring, @Nonnull final PyMemberInfoStorage infoStorage, @Nonnull final Editor editor)
	{
		//TODO: Move to vp (presenter) as well
		final PyPullUpNothingToRefactorMessage nothingToRefactor = new PyPullUpNothingToRefactorMessage(project, editor, classUnderRefactoring);

		if(PyAncestorsUtils.getAncestorsUnderUserControl(classUnderRefactoring).isEmpty())
		{
			nothingToRefactor.showNothingToRefactor();
			return;
		}


		ViewPresenterUtils.linkViewWithPresenterAndLaunch(PyPullUpPresenter.class, PyPullUpView.class, new Creator<PyPullUpView, PyPullUpPresenter>()
		{
			@Nonnull
			@Override
			public PyPullUpPresenter createPresenter(@Nonnull final PyPullUpView view)
			{
				return new PyPullUpPresenterImpl(view, infoStorage, classUnderRefactoring);
			}

			@Nonnull
			@Override
			public PyPullUpView createView(@Nonnull final PyPullUpPresenter presenter)
			{
				return new PyPullUpViewSwingImpl(project, presenter, classUnderRefactoring, nothingToRefactor);
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
		return "refactoring.pullMembersUp";
	}
}
