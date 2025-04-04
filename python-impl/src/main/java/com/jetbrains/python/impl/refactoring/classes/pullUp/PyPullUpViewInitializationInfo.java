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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;

/**
 * Configuration for pull-up view
 *
 * @author Ilya.Kazakevich
 */
class PyPullUpViewInitializationInfo extends MembersViewInitializationInfo
{
	@Nonnull
	private final Collection<PyClass> myParents;

	/**
	 * @param parents list of possible parents to display.
	 */
	PyPullUpViewInitializationInfo(@Nonnull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
			@Nonnull final List<PyMemberInfo<PyElement>> memberInfos,
			@Nonnull final Collection<PyClass> parents)
	{
		super(memberInfoModel, memberInfos);
		myParents = new ArrayList<>(parents);
	}

	@Nonnull
	public Collection<PyClass> getParents()
	{
		return Collections.unmodifiableCollection(myParents);
	}
}
