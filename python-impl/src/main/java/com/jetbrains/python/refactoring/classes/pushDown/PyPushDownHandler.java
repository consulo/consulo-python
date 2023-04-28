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
package com.jetbrains.python.refactoring.classes.pushDown;

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.project.Project;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.application.util.query.Query;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.vp.Creator;
import com.jetbrains.python.vp.ViewPresenterUtils;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownHandler extends PyClassRefactoringHandler
{
	public static final String REFACTORING_NAME = RefactoringBundle.message("push.members.down.title");

	@Override
	protected void doRefactorImpl(@Nonnull final Project project, @Nonnull final PyClass classUnderRefactoring, @Nonnull final PyMemberInfoStorage infoStorage, @Nonnull Editor editor)
	{

		//TODO: Move to presenter?
		final Query<PyClass> query = PyClassInheritorsSearch.search(classUnderRefactoring, false);
		if(query.findFirst() == null)
		{
			final String message = RefactoringBundle.message("class.0.does.not.have.inheritors", classUnderRefactoring.getName());
			CommonRefactoringUtil.showErrorHint(project, editor, message, getTitle(), getHelpId());
			return;
		}

		ViewPresenterUtils.linkViewWithPresenterAndLaunch(PyPushDownPresenter.class, PyPushDownView.class, new Creator<PyPushDownView, PyPushDownPresenter>()
		{
			@Nonnull
			@Override
			public PyPushDownPresenter createPresenter(@Nonnull PyPushDownView view)
			{
				return new PyPushDownPresenterImpl(project, view, classUnderRefactoring, infoStorage);
			}

			@Nonnull
			@Override
			public PyPushDownView createView(@Nonnull PyPushDownPresenter presenter)
			{
				return new PyPushDownViewSwingImpl(classUnderRefactoring, project, presenter);
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
		return "members.push.down";
	}
}
