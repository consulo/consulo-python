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

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
@ExtensionImpl
public class PyStringReferenceSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor {
  public void processQuery(@Nonnull final ReferencesSearch.SearchParameters params,
                           @Nonnull final Processor<? super PsiReference> consumer) {
    final PsiElement element = params.getElementToSearch();
    if (!(element instanceof PyElement) && !(element instanceof PsiDirectory)) {
      return;
    }

    String name;
    SearchScope searchScope = ReadAction.compute(() -> {
      SearchScope s = params.getEffectiveSearchScope();
      if (s instanceof GlobalSearchScope) {
        s = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)s, PythonFileType.INSTANCE);
      }
      return s;
    });

    name = ReadAction.compute(() -> PyUtil.computeElementNameForStringSearch(element));

    if (StringUtil.isEmpty(name)) {
      return;
    }
    
    params.getOptimizer().searchWord(name, searchScope, UsageSearchContext.IN_STRINGS, true, element);
  }
}
