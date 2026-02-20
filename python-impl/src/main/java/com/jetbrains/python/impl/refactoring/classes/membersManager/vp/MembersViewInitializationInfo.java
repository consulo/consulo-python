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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import jakarta.annotation.Nonnull;

import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;

/**
 * Configuration for {@link MembersBasedView}
 *
 * @author Ilya.Kazakevich
 */
public class MembersViewInitializationInfo
{

	@Nonnull
	private final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> myMemberInfoModel;
	@Nonnull
	private final Collection<PyMemberInfo<PyElement>> myMemberInfos;

	/**
	 * @param memberInfoModel model to be used in members panel
	 * @param memberInfos     members to displau
	 */
	public MembersViewInitializationInfo(@Nonnull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel, @Nonnull Collection<PyMemberInfo<PyElement>> memberInfos)
	{
		myMemberInfos = new ArrayList<>(memberInfos);
		myMemberInfoModel = memberInfoModel;
	}

	/**
	 * @return model to be used in members panel
	 */
	@Nonnull
	public MemberInfoModel<PyElement, PyMemberInfo<PyElement>> getMemberInfoModel()
	{
		return myMemberInfoModel;
	}

	/**
	 * @return members to display
	 */
	@Nonnull
	public Collection<PyMemberInfo<PyElement>> getMemberInfos()
	{
		return Collections.unmodifiableCollection(myMemberInfos);
	}
}
