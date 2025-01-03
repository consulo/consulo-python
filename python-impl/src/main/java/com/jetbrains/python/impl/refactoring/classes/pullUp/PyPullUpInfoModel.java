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

import consulo.language.editor.refactoring.classMember.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;

/**
 * Dependencies model for PyPullUp refactoring
 *
 * @author Ilya.Kazakevich
 */
class PyPullUpInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>>
{
	@Nonnull
	private final PyPullUpView myView;


	PyPullUpInfoModel(@Nonnull final PyClass classUnderRefactoring, @Nonnull final PyPullUpView view)
	{
		super(classUnderRefactoring, null, false);
		myView = view;
	}

	@Override
	public boolean isAbstractEnabled(final PyMemberInfo<PyElement> member)
	{
		return member.isCouldBeAbstract() && isMemberEnabled(member); // TODO: copy paste with other models, get rid of
	}

	@Override
	public int checkForProblems(@Nonnull final PyMemberInfo<PyElement> member)
	{
		return member.isChecked() ? OK : super.checkForProblems(member);
	}


	@Override
	protected int doCheck(@Nonnull final PyMemberInfo<PyElement> memberInfo, final int problem)
	{
		return problem;
	}

	@Override
	public boolean isMemberEnabled(final PyMemberInfo<PyElement> member)
	{
		final PyClass currentSuperClass = myView.getSelectedParent();
		if(member.getMember() instanceof PyClass)
		{
			//TODO: Delegate to Memebers Managers
			final PyClass memberClass = (PyClass) member.getMember();
			if(memberClass.isSubclass(currentSuperClass, null) || currentSuperClass.isSubclass(memberClass, null))
			{
				return false; //Class is already parent of superclass
			}
		}
		return true;
	}
}
