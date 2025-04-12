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
import consulo.application.AccessRule;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.util.query.QueryExecutorBase;
import jakarta.annotation.Nonnull;

import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class PyInitReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor {
    @Override
    public void processQuery(
        @Nonnull ReferencesSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiReference> consumer
    ) {
        PsiElement element = queryParameters.getElementToSearch();
        if (!(element instanceof PyFunction function)) {
            return;
        }

        if (!PyNames.INIT.equals(AccessRule.read(function::getName))) {
            return;
        }
        PyClass pyClass = AccessRule.read(function::getContainingClass);
        if (pyClass == null) {
            return;
        }
        String className = AccessRule.read(pyClass::getName);
        if (className == null) {
            return;
        }

        SearchScope searchScope = queryParameters.getEffectiveSearchScope();
        if (searchScope instanceof GlobalSearchScope globalSearchScope) {
            searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(globalSearchScope, PythonFileType.INSTANCE);
        }

        queryParameters.getOptimizer().searchWord(className, searchScope, UsageSearchContext.IN_CODE, true, function);
    }
}
