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
package com.jetbrains.python.psi.resolve;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.PsiScopeProcessor;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;

/**
 * @author vlan
 *         <p>
 *         TODO: Merge it with {@link ScopeUtil}
 */
public class PyResolveUtil
{
	private PyResolveUtil()
	{
	}

	/**
	 * Crawls up scopes of the PSI tree, checking named elements and name definers.
	 */
	public static void scopeCrawlUp(@Nonnull PsiScopeProcessor processor, @Nonnull PsiElement element, @Nullable String name, @Nullable PsiElement roof)
	{
		// Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
		final PsiElement realContext = PyPsiUtils.getRealContext(element);
		final ScopeOwner originalOwner;
		if(realContext != element && realContext instanceof PyFile)
		{
			originalOwner = (PyFile) realContext;
		}
		else
		{
			originalOwner = ScopeUtil.getScopeOwner(realContext);
		}
		final PsiElement parent = element.getParent();
		final boolean isGlobalOrNonlocal = parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement;
		ScopeOwner owner = originalOwner;
		if(isGlobalOrNonlocal)
		{
			final ScopeOwner outerScopeOwner = ScopeUtil.getScopeOwner(owner);
			if(outerScopeOwner != null)
			{
				owner = outerScopeOwner;
			}
		}
		scopeCrawlUp(processor, owner, originalOwner, name, roof);
	}

	public static void scopeCrawlUp(@Nonnull PsiScopeProcessor processor, @Nonnull ScopeOwner scopeOwner, @Nullable String name, @Nullable PsiElement roof)
	{
		scopeCrawlUp(processor, scopeOwner, scopeOwner, name, roof);
	}

	public static void scopeCrawlUp(@Nonnull PsiScopeProcessor processor, @Nullable ScopeOwner scopeOwner, @Nullable ScopeOwner originalScopeOwner, @Nullable String name, @Nullable PsiElement roof)
	{
		while(scopeOwner != null)
		{
			if(!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner)
			{
				final Scope scope = ControlFlowCache.getScope(scopeOwner);
				if(name != null)
				{
					final boolean includeNestedGlobals = scopeOwner instanceof PyFile;
					for(PsiNamedElement resolved : scope.getNamedElements(name, includeNestedGlobals))
					{
						if(!processor.execute(resolved, ResolveState.initial()))
						{
							return;
						}
					}
				}
				else
				{
					for(PsiNamedElement element : scope.getNamedElements())
					{
						if(!processor.execute(element, ResolveState.initial()))
						{
							return;
						}
					}
				}
				for(PyImportedNameDefiner definer : scope.getImportedNameDefiners())
				{
					if(!processor.execute(definer, ResolveState.initial()))
					{
						return;
					}
				}
			}
			if(scopeOwner == roof)
			{
				return;
			}
			scopeOwner = ScopeUtil.getScopeOwner(scopeOwner);
		}
	}

	@Nonnull
	public static Collection<PsiElement> resolveLocally(@Nonnull PyReferenceExpression referenceExpression)
	{
		final String referenceName = referenceExpression.getName();

		if(referenceName == null)
		{
			return Collections.emptyList();
		}

		final PyResolveProcessor processor = new PyResolveProcessor(referenceName, true);
		scopeCrawlUp(processor, referenceExpression, referenceName, null);

		return processor.getElements();
	}

	@Nonnull
	public static Collection<PsiElement> resolveLocally(@Nonnull ScopeOwner scopeOwner, @Nonnull String name)
	{
		final PyResolveProcessor processor = new PyResolveProcessor(name, true);
		scopeCrawlUp(processor, scopeOwner, name, null);

		return processor.getElements();
	}
}
