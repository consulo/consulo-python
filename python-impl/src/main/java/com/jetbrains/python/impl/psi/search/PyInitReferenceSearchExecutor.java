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

package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.util.query.QueryExecutorBase;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyInitReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor
{
	@Override
	public void processQuery(@Nonnull ReferencesSearch.SearchParameters queryParameters, @Nonnull final Processor<? super PsiReference> consumer)
	{
		PsiElement element = queryParameters.getElementToSearch();
		if(!(element instanceof PyFunction))
		{
			return;
		}

		String className;
		SearchScope searchScope;
		PyFunction function;
		function = (PyFunction) element;
		if(!PyNames.INIT.equals(ReadAction.compute(() -> function.getName())))
		{
			return;
		}
		final PyClass pyClass = ReadAction.compute(() -> function.getContainingClass());
		if(pyClass == null)
		{
			return;
		}
		className = ReadAction.compute(() -> pyClass.getName());
		if(className == null)
		{
			return;
		}

		searchScope = queryParameters.getEffectiveSearchScope();
		if(searchScope instanceof GlobalSearchScope)
		{
			searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope) searchScope, PythonFileType.INSTANCE);
		}


		queryParameters.getOptimizer().searchWord(className, searchScope, UsageSearchContext.IN_CODE, true, function);
	}
}
