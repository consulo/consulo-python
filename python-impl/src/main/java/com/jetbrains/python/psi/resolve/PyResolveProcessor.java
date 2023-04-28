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
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.PsiScopeProcessor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ResolveResultList;

/**
 * @author vlan
 */
public class PyResolveProcessor implements PsiScopeProcessor
{
	@Nonnull
	private final String myName;
	private final boolean myLocalResolve;
	@Nonnull
	private final Map<PsiElement, PyImportedNameDefiner> myResults = Maps.newLinkedHashMap();
	@Nonnull
	private final Map<PsiElement, PyImportedNameDefiner> myImplicitlyImportedResults = Maps.newLinkedHashMap();
	@Nullable
	private ScopeOwner myOwner;

	public PyResolveProcessor(@Nonnull String name)
	{
		this(name, false);
	}

	public PyResolveProcessor(@Nonnull String name, boolean localResolve)
	{
		myName = name;
		myLocalResolve = localResolve;
	}

	@Override
	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state)
	{
		final PsiNamedElement namedElement = PyUtil.as(element, PsiNamedElement.class);
		if(namedElement != null && myName.equals(namedElement.getName()))
		{
			return tryAddResult(element, null);
		}
		final PyImportedNameDefiner importedNameDefiner = PyUtil.as(element, PyImportedNameDefiner.class);
		if(importedNameDefiner != null)
		{
			final List<RatedResolveResult> results = resolveInImportedNameDefiner(importedNameDefiner);
			if(!results.isEmpty())
			{
				boolean cont = true;
				for(RatedResolveResult result : results)
				{
					final PsiElement resolved = result.getElement();
					if(resolved != null)
					{
						cont = tryAddResult(resolved, importedNameDefiner) && cont;
					}
				}
				return cont;
			}
			final PyImportElement importElement = PyUtil.as(element, PyImportElement.class);
			if(importElement != null)
			{
				final String importName = importElement.getVisibleName();
				if(myName.equals(importName))
				{
					return tryAddResult(null, importElement);
				}
			}
		}
		return myOwner == null || myOwner == ScopeUtil.getScopeOwner(element);
	}

	@Nullable
	@Override
	public <T> T getHint(@Nonnull Key<T> hintKey)
	{
		return null;
	}

	@Override
	public void handleEvent(@Nonnull Event event, @Nullable Object associated)
	{
	}

	@Nonnull
	public Map<PsiElement, PyImportedNameDefiner> getResults()
	{
		return myResults.isEmpty() ? myImplicitlyImportedResults : myResults;
	}

	@Nonnull
	public Collection<PsiElement> getElements()
	{
		return getResults().keySet();
	}

	@Nullable
	public ScopeOwner getOwner()
	{
		return myOwner;
	}

	@Nonnull
	private List<RatedResolveResult> resolveInImportedNameDefiner(@Nonnull PyImportedNameDefiner definer)
	{
		if(myLocalResolve)
		{
			final PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
			if(importElement != null)
			{
				return ResolveResultList.to(importElement.getElementNamed(myName, false));
			}
			else
			{
				return Collections.emptyList();
			}
		}
		return definer.multiResolveName(myName);
	}

	private boolean tryAddResult(@Nullable PsiElement element, @Nullable PyImportedNameDefiner definer)
	{
		final ScopeOwner owner = ScopeUtil.getScopeOwner(definer != null ? definer : element);
		if(myOwner == null)
		{
			myOwner = owner;
		}
		final boolean sameScope = owner == myOwner;
		if(sameScope)
		{
			// XXX: In 'from foo import foo' inside __init__.py the preferred result is explicitly imported 'foo'
			if(definer instanceof PyFromImportStatement)
			{
				myImplicitlyImportedResults.put(element, definer);
			}
			else
			{
				myResults.put(element, definer);
			}
		}
		return sameScope;
	}
}
