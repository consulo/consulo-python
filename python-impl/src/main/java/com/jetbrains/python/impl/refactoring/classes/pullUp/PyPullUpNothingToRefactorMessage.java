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

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.project.Project;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;

/**
 * Displays "nothing to refactor" message
 *
 * @author Ilya.Kazakevich
 */
class PyPullUpNothingToRefactorMessage
{

	@Nonnull
	private final Project myProject;
	@Nonnull
	private final Editor myEditor;
	@Nonnull
	private final PyClass myClassUnderRefactoring;

	/**
	 * @param project               project to be used
	 * @param editor                editor to be used
	 * @param classUnderRefactoring class user refactors
	 */
	PyPullUpNothingToRefactorMessage(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PyClass classUnderRefactoring)
	{
		myProject = project;
		myEditor = editor;
		myClassUnderRefactoring = classUnderRefactoring;
	}

	/**
	 * Display message
	 */
	void showNothingToRefactor()
	{
		CommonRefactoringUtil.showErrorHint(myProject, myEditor, PyBundle.message("refactoring.pull.up.error.cannot.perform.refactoring.no.base.classes", myClassUnderRefactoring.getName()),
				RefactoringBundle.message("pull.members.up.title"), "members.pull.up");
	}
}
