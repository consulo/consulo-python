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
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import java.util.Collection;

import jakarta.annotation.Nonnull;

import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;

/**
 * Displays error messages about fact that destination class already contains some member infos
 * or members under refactoring would not be available at the new place.
 *
 * @author Ilya.Kazakevich
 */
public class MembersConflictDialog extends ConflictsDialog
{
	/**
	 * @param project               project under refactoring
	 * @param duplicatesConflict    duplicates conflicts : that means destination class has the same member.
	 *                              If member "foo" already exists in class "bar": pass [bar] -] [foo].
	 * @param dependenciesConflicts dependency conflict: list of elements used by member under refactoring and would not be available
	 *                              at new destination. If user wants to move method, that uses field "bar" which would not be available at new class,
	 *                              pass [bar] field
	 */
	public MembersConflictDialog(@Nonnull final Project project, @Nonnull final MultiMap<PyClass, PyMemberInfo<?>> duplicatesConflict, @Nonnull final Collection<PyMemberInfo<?>>
			dependenciesConflicts)
	{
		super(project, convertDescription(duplicatesConflict, dependenciesConflicts), null, true, false);
	}

	@Nonnull
	private static MultiMap<PsiElement, String> convertDescription(@Nonnull final MultiMap<PyClass, PyMemberInfo<?>> duplicateConflictDescriptions,
			@Nonnull final Collection<PyMemberInfo<?>> dependenciesConflicts)
	{
		final MultiMap<PsiElement, String> result = new MultiMap<>();
		for(final PyClass aClass : duplicateConflictDescriptions.keySet())
		{
			for(final PyMemberInfo<?> pyMemberInfo : duplicateConflictDescriptions.get(aClass))
			{
				final String message = RefactoringBundle.message("0.already.contains.a.1", RefactoringUIUtil.getDescription(aClass, false), RefactoringUIUtil.getDescription(pyMemberInfo.getMember(),
						false));
				result.putValue(aClass, message);
			}
		}

		for(final PyMemberInfo<?> memberUnderConflict : dependenciesConflicts)
		{
			result.putValue(memberUnderConflict.getMember(), PyBundle.message("refactoring.will.not.be.accessible", RefactoringUIUtil.getDescription(memberUnderConflict.getMember(), false)));
		}


		return result;
	}
}
