/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.refactoring.move.moduleMembers;

import jakarta.annotation.Nonnull;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.editor.refactoring.classMember.DependentMembersCollectorBase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * Collects dependencies of the top-level symbols in the given module. This information is used then to highlight them
 * in "Move" dialog the same way as it's done for members of classes in various class-related refactorings.
 *
 * @author Mikhail Golubev
 * @see PyModuleMemberInfoModel
 */
public class PyDependentModuleMembersCollector extends DependentMembersCollectorBase<PsiNamedElement, PyFile>
{
	private final PyFile myModule;

	public PyDependentModuleMembersCollector(@Nonnull PyFile module)
	{
		super(module, null);
		myModule = module;
	}

	@Override
	public void collect(final PsiNamedElement member)
	{
		if(member.getContainingFile() == myModule)
		{
			final PyResolveContext resolveContext = PyResolveContext.defaultContext();
			final PsiElement memberBody = PyMoveModuleMembersHelper.expandNamedElementBody(member);
			assert memberBody != null;
			memberBody.accept(new PyRecursiveElementVisitor()
			{
				@Override
				public void visitElement(PsiElement element)
				{
					for(PsiElement result : PyUtil.multiResolveTopPriority(element, resolveContext))
					{
						if(result != null && isValidSameModuleDependency(result) && result != member)
						{
							myCollection.add((PsiNamedElement) result);
						}
					}
					super.visitElement(element);
				}
			});
		}
	}

	private boolean isValidSameModuleDependency(@Nonnull PsiElement element)
	{
		return PyMoveModuleMembersHelper.isMovableModuleMember(element) && element.getContainingFile() == myModule;
	}
}
