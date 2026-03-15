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

import java.util.Collection;


import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;

/**
 * View configuration for "extract superclass"
 *
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassInitializationInfo extends MembersViewInitializationInfo
{

	private final String myDefaultFilePath;
	private final VirtualFile[] myRoots;

	/**
	 * @param defaultFilePath module file path to display. User will be able to change it later.
	 * @param roots           virtual files where user may add new module
	 */
	PyExtractSuperclassInitializationInfo(MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
			Collection<PyMemberInfo<PyElement>> memberInfos,
			String defaultFilePath,
			VirtualFile... roots)
	{
		super(memberInfoModel, memberInfos);
		myDefaultFilePath = defaultFilePath;
		myRoots = roots.clone();
	}

	public String getDefaultFilePath()
	{
		return myDefaultFilePath;
	}

	public VirtualFile[] getRoots()
	{
		return myRoots.clone();
	}
}
