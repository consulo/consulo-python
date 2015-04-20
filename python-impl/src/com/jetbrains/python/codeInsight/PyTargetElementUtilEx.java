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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInsight.TargetElementUtilEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * @author yole
 */
public class PyTargetElementUtilEx extends TargetElementUtilEx.Adapter
{
	@Nullable
	@Override
	public PsiElement getReferenceOrReferencedElement(@NotNull PsiReference ref, @NotNull Set<String> flags)
	{
		if(!flags.contains(ELEMENT_NAME_ACCEPTED))
		{
			return null;
		}

		final PsiElement element = ref.getElement();
		PsiElement result = ref.resolve();
		Set<PsiElement> visited = new HashSet<PsiElement>();
		visited.add(result);
		while(result instanceof PyReferenceExpression || result instanceof PyTargetExpression)
		{
			PsiElement nextResult = ((PyQualifiedExpression) result).getReference(PyResolveContext.noImplicits()).resolve();
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
