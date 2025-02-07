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

import jakarta.annotation.Nonnull;

import consulo.util.collection.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;

/**
 * Recursive visitor with multimap, to be used for {@link MembersManager#getDependencies(com.jetbrains.python.psi.PyElement)}
 */
class PyRecursiveElementVisitorWithResult extends PyRecursiveElementVisitor
{
	@Nonnull
	protected final MultiMap<PyClass, PyElement> myResult;

	PyRecursiveElementVisitorWithResult()
	{
		myResult = new MultiMap<>();
	}
}
