/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.findUsages;

import java.util.Collection;
import java.util.Collections;

import jakarta.annotation.Nonnull;

import consulo.find.FindUsagesHandler;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyClassFindUsagesHandler extends FindUsagesHandler
{
	private final PyClass myClass;

	public PyClassFindUsagesHandler(@Nonnull PyClass psiElement)
	{
		super(psiElement);
		myClass = psiElement;
	}

	@Nonnull
	@Override
	public PsiElement[] getSecondaryElements()
	{
		final PyFunction initMethod = myClass.findMethodByName(PyNames.INIT, false, null);
		if(initMethod != null)
		{
			return new PsiElement[]{initMethod};
		}
		return PsiElement.EMPTY_ARRAY;
	}

	@Override
	protected boolean isSearchForTextOccurrencesAvailable(@Nonnull PsiElement psiElement, boolean isSingleFile)
	{
		return true;
	}

	@Override
	protected Collection<String> getStringsToSearch(@Nonnull PsiElement element)
	{
		if(element instanceof PyFunction && PyNames.INIT.equals(((PyFunction) element).getName()))
		{
			return Collections.emptyList();
		}
		return super.getStringsToSearch(element);
	}
}
