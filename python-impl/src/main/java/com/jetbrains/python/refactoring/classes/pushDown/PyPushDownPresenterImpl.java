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

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;

/**
 * @author Ilya.Kazakevich
 */
public class PyPushDownPresenterImpl extends MembersBasedPresenterWithPreviewImpl<PyPushDownView, MemberInfoModel<PyElement, PyMemberInfo<PyElement>>> implements PyPushDownPresenter
{
	@Nonnull
	private final Project myProject;

	public PyPushDownPresenterImpl(@Nonnull final Project project, @Nonnull final PyPushDownView view, @Nonnull final PyClass classUnderRefactoring, @Nonnull final PyMemberInfoStorage infoStorage)
	{
		super(view, classUnderRefactoring, infoStorage, new UsedByDependencyMemberInfoModel<>(classUnderRefactoring));
		myProject = project;
	}

	@Nonnull
	@Override
	public BaseRefactoringProcessor createProcessor()
	{
		return new PyPushDownProcessor(myProject, myView.getSelectedMemberInfos(), myClassUnderRefactoring);
	}

	@Nonnull
	@Override
	protected Iterable<? extends PyClass> getDestClassesToCheckConflicts()
	{
		return PyPushDownProcessor.getInheritors(myClassUnderRefactoring);
	}

	@Override
	public void launch()
	{
		myView.configure(new MembersViewInitializationInfo(myModel, PyUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring))));
		myView.initAndShow();
	}
}
