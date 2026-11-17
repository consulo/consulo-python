// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ExtensionImpl;
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

import java.util.function.Predicate;

@ExtensionImpl
public final class PyStringReferenceSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {

    public PyStringReferenceSearch() {
        super(true);
    }

    @Override
    public void processQuery(ReferencesSearch.SearchParameters params,
                             Predicate<? super PsiReference> consumer) {
        PsiElement element = params.getElementToSearch();
        if (!(element instanceof PyElement) && !(element instanceof PsiDirectory)) {
            return;
        }

        SearchScope searchScope = params.getEffectiveSearchScope();
        if (searchScope instanceof GlobalSearchScope) {
            searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
        }

        String name = PySearchUtil.computeElementNameForStringSearch(element);

        if (StringUtil.isEmpty(name)) {
            return;
        }
        params.getOptimizer().searchWord(name, searchScope, UsageSearchContext.IN_STRINGS, true, element);
    }
}
