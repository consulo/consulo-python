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

import consulo.language.editor.refactoring.classMember.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>>
{
	PyExtractSuperclassInfoModel(@Nonnull PyClass clazz)
	{
		super(clazz, null, false);
	}

	@Override
	public boolean isAbstractEnabled(PyMemberInfo<PyElement> member)
	{
		return member.isCouldBeAbstract() && isMemberEnabled(member);
	}

	@Override
	public int checkForProblems(@Nonnull PyMemberInfo<PyElement> member)
	{
		return member.isChecked() ? OK : super.checkForProblems(member);
	}

	@Override
	protected int doCheck(@Nonnull PyMemberInfo<PyElement> memberInfo, int problem)
	{
		return problem;
	}
}
