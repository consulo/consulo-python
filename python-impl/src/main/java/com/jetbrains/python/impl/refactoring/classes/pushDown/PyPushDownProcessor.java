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
package com.jetbrains.python.impl.refactoring.classes.pushDown;

import java.util.Collection;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.project.Project;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.usage.UsageViewBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownProcessor extends PyMembersRefactoringBaseProcessor
{

	private static final String HEADER = RefactoringBundle.message("push.down.members.elements.header", "");

	public PyPushDownProcessor(@Nonnull final Project project, @Nonnull final Collection<PyMemberInfo<PyElement>> membersToMove, @Nonnull final PyClass from)
	{
		super(project, membersToMove, from, getChildren(from));
	}

	@Nonnull
	private static PyClass[] getChildren(@Nonnull final PyClass from)
	{
		final Collection<PyClass> all = getInheritors(from);
		return all.toArray(new PyClass[all.size()]);
	}

	/**
	 * @param from class to check for inheritors
	 * @return inheritors of class
	 */
	@Nonnull
	static Collection<PyClass> getInheritors(@Nonnull final PyClass from)
	{
		return PyClassInheritorsSearch.search(from, false).findAll();
	}


	public String getProcessedElementsHeader()
	{
		return HEADER;
	}

	public String getCodeReferencesText(int usagesCount, int filesCount)
	{
		return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
	}

	@Nullable
	public String getCommentReferencesText(int usagesCount, int filesCount)
	{
		return null;
	}

	@Override
	protected String getCommandName()
	{
		return PyPushDownHandler.REFACTORING_NAME;
	}

	@Nullable
	@Override
	protected String getRefactoringId()
	{
		return "refactoring.python.push.down";
	}
}
