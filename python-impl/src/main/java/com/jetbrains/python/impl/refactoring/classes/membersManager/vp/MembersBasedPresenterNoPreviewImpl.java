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
package com.jetbrains.python.impl.refactoring.classes.membersManager.vp;

import jakarta.annotation.Nonnull;

import consulo.application.ApplicationManager;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.undoRedo.CommandProcessor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;

/**
 * Presenter that has not preview. Children should implement {@link #refactorNoPreview()}.
 * To "preview" button would be displayed
 *
 * @param <T> view for this presenter
 * @param <M> Type of model
 * @author Ilya.Kazakevich
 */

public abstract class MembersBasedPresenterNoPreviewImpl<T extends MembersBasedView<?>, M extends MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> extends MembersBasedPresenterImpl<T, M>
{
	/**
	 * @param view                  view for this presenter
	 * @param classUnderRefactoring class to refactor
	 * @param infoStorage           info storage
	 * @param model                 Member model (to be used for dependencies checking)
	 */
	protected MembersBasedPresenterNoPreviewImpl(@Nonnull final T view, @Nonnull final PyClass classUnderRefactoring, @Nonnull final PyMemberInfoStorage infoStorage, @Nonnull final M model)
	{
		super(view, classUnderRefactoring, infoStorage, model);
	}

	@Override
	public boolean showPreview()
	{
		return false;
	}

	@Override
	void doRefactor()
	{
		CommandProcessor.getInstance().executeCommand(myClassUnderRefactoring.getProject(), () -> ApplicationManager.getApplication().runWriteAction(new MyRunnableRefactoring()), getCommandName(),
				null);
		myView.close();
	}

	/**
	 * @return Command name for this preview
	 */
	@Nonnull
	protected abstract String getCommandName();

	/**
	 * Do refactor with out of preview. Implement this method to do refactoring.
	 */
	protected abstract void refactorNoPreview();

	private class MyRunnableRefactoring implements Runnable
	{
		@Override
		public void run()
		{
			refactorNoPreview();
		}
	}
}
