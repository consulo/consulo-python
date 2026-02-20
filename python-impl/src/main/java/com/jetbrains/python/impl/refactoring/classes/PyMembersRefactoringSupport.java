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
package com.jetbrains.python.impl.refactoring.classes;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.classMember.ClassMembersRefactoringSupport;
import consulo.language.editor.refactoring.classMember.DependentMembersCollectorBase;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.move.moduleMembers.PyDependentModuleMembersCollector;

import jakarta.annotation.Nonnull;

/**
 * @author Dennis.Ushakov
 */
@ExtensionImpl
public class PyMembersRefactoringSupport implements ClassMembersRefactoringSupport
{
	public static PyMemberInfoStorage getSelectedMemberInfos(PyClass clazz, PsiElement element1, PsiElement element2)
	{
		PyMemberInfoStorage infoStorage = new PyMemberInfoStorage(clazz);
		for(PyMemberInfo<PyElement> member : infoStorage.getClassMemberInfos(clazz))
		{
			PyElement function = member.getMember();
			member.setChecked(PsiTreeUtil.isAncestor(function, element1, false) || PsiTreeUtil.isAncestor(function, element2, false));
		}
		return infoStorage;
	}

	public DependentMembersCollectorBase createDependentMembersCollector(Object clazz, Object superClass)
	{
		if(clazz instanceof PyClass)
		{
			return new PyDependentClassMembersCollector((PyClass) clazz, (PyClass) superClass);
		}
		else if(clazz instanceof PyFile)
		{
			return new PyDependentModuleMembersCollector(((PyFile) clazz));
		}
		return null;
	}

	public boolean isProperMember(MemberInfoBase member)
	{
		return true;
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
