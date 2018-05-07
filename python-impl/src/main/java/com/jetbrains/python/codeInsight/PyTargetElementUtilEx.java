/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.codeInsight;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.codeInsight.TargetElementUtilEx;
import consulo.extensions.CompositeExtensionPointName;

/**
 * @author yole
 */
public class PyTargetElementUtilEx extends TargetElementUtilEx.Adapter
{
	@Override
	@CompositeExtensionPointName.BooleanBreakResult(breakValue = false)
	public boolean includeSelfInGotoImplementation(@Nonnull PsiElement element)
	{
		return element.getLanguage() != PythonLanguage.getInstance();
	}

	@Nullable
	@Override
	public PsiElement getReferenceOrReferencedElement(@Nonnull PsiReference ref, @Nonnull Set<String> flags)
	{
		if(!flags.contains(ELEMENT_NAME_ACCEPTED))
		{
			return null;
		}

		final PsiElement element = ref.getElement();
		PsiElement result = ref.resolve();
		Set<PsiElement> visited = new HashSet<>();
		visited.add(result);
		while(result instanceof PyReferenceOwner && (result instanceof PyReferenceExpression || result instanceof PyTargetExpression))
		{
			PsiElement nextResult = ((PyReferenceOwner) result).getReference(PyResolveContext.noImplicits()).resolve();
			if(nextResult != null && !visited.contains(nextResult) &&
					PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(result, ScopeOwner.class) &&
					(nextResult instanceof PyReferenceExpression || nextResult instanceof PyTargetExpression || nextResult instanceof PyParameter))
			{
				visited.add(nextResult);
				result = nextResult;
			}
			else
			{
				break;
			}
		}
		return result;
	}
}
