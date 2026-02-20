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

import java.util.ArrayList;
import java.util.Collection;

import jakarta.annotation.Nonnull;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;

/**
 * Moves members checking types at runtime.
 *
 * @author Ilya.Kazakevich
 */
class TypeSafeMovingStrategy<T extends PyElement>
{
	@Nonnull
	private final PyClass myFrom;
	@Nonnull
	private final MembersManager<T> myManager;
	@Nonnull
	private final Collection<PyMemberInfo<T>> myMemberInfoCollection;
	@Nonnull
	private final PyClass[] myTo;

	/**
	 * Move members.
	 *
	 * @param from                 source
	 * @param manager              manager to be used
	 * @param memberInfoCollection what to move
	 * @param to                   where
	 */
	@SuppressWarnings({
			"unchecked",
			"rawtypes"
	}) //We check types at runtime
	static void moveCheckingTypesAtRunTime(@Nonnull PyClass from,
			@Nonnull MembersManager<?> manager,
			@Nonnull Collection<PyMemberInfo<PyElement>> memberInfoCollection,
			@Nonnull PyClass... to)
	{
		manager.checkElementTypes((Collection) MembersManager.fetchElements(memberInfoCollection));
		new TypeSafeMovingStrategy(from, manager, memberInfoCollection, to).moveTyped();
	}

	private TypeSafeMovingStrategy(@Nonnull PyClass from, @Nonnull MembersManager<T> manager, @Nonnull Collection<PyMemberInfo<T>> memberInfoCollection, @Nonnull PyClass[] to)
	{
		myFrom = from;
		myManager = manager;
		myMemberInfoCollection = new ArrayList<>(memberInfoCollection);
		myTo = to.clone();
	}


	/**
	 * While types are already checked at runtime, this method could move everything in type-safe manner.
	 */
	private void moveTyped()
	{
		Collection<T> elementsCollection = MembersManager.fetchElements(myMemberInfoCollection);
		Collection<? extends PyElement> references = myManager.getElementsToStoreReferences(elementsCollection);

		// Store references to add required imports
		for(PyElement element : references)
		{
			PyClassRefactoringUtil.rememberNamedReferences(element, PyNames.CANONICAL_SELF); //"self" is not reference we need to move
		}

		// Move
		Collection<PyElement> newElements = myManager.moveMembers(myFrom, myMemberInfoCollection, myTo);

		// Restore references to add appropriate imports
		for(PyElement element : newElements)
		{
			PyClassRefactoringUtil.restoreNamedReferences(element);
		}
	}
}
